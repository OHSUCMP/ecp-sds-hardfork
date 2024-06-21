package edu.ohsu.cmp.ecp.sds;

import static java.util.stream.Collectors.filtering;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.rest.api.RestSearchParameterTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.util.FhirTerser;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;

@Component
public class SupplementalDataStoreResourceCreation {

	@Inject
	SupplementalDataStorePartition partition;

	public interface Details {

		String resourceType();

		boolean inherentlyClaimsCompartment(IIdType patientCompartment);

		String partitionName();

		Optional<IIdType> qualifiedResourceId();

		Set<CompartmentDetails> compartmentDetails();
		
		Set<IIdType> compartments();

	}
	
	public interface CompartmentDetails {
		
		boolean ownerIsCreatedResource() ;
		
		Optional<IIdType> owner() ;

	}
	
	public Optional<Details> resourceCreationInfo( RequestDetails theRequestDetails ) {
		// return early if not a resource write
		if ( !isResourceWrite(theRequestDetails) )
			return Optional.empty();

		IBaseResource createdResource = theRequestDetails.getResource();
		// return early if there is no resource
		if ( null == createdResource )
			return Optional.empty();

		String createdInPartitionName = partition.partitionNameFromRequest(theRequestDetails) ;

		Set<CompartmentDetails> compartmentsOfCreatedResource = new HashSet<>();
		if ( "Patient".equals( createdResource.fhirType() ) ) {
			// a Patient resource is in the Patient compartment

			Optional<IIdType> createdResourceId = patientCompartmentOfCreatedPatientResource( createdResource, createdInPartitionName ) ;
			compartmentsOfCreatedResource.add( DetailsImpl.compartmentOwnedByCreatedResource( createdResourceId ) ) ;

		} else {
			// a non-Patient resource may be in Patient compartments according to the spec

			FhirContext fhirContext = theRequestDetails.getFhirContext() ;
			FhirTerser terser = fhirContext.newTerser();

			RuntimeResourceDefinition sourceDef = fhirContext.getResourceDefinition( createdResource );
			for( RuntimeSearchParam runtimeSearchParam : sourceDef.getSearchParamsForCompartmentName( "Patient" ) ) {
				if ( null == runtimeSearchParam.getProvidesMembershipInCompartments() )
					continue ;
				if ( !runtimeSearchParam.getProvidesMembershipInCompartments().contains( "Patient" ) )
					continue ;
				if ( runtimeSearchParam.getParamType() != RestSearchParameterTypeEnum.REFERENCE )
					continue ;
				Object searchParamValue = terser.getSingleValueOrNull(createdResource, runtimeSearchParam.getPath() ) ;
				if ( null == searchParamValue )
					continue ;
				if ( !(searchParamValue instanceof IBaseReference) )
					continue ;
				IIdType owner = ((IBaseReference)searchParamValue).getReferenceElement() ;
				if ( !"Patient".equals(owner.getResourceType() ) )
					continue ;
				IIdType fullyQualifiedOwner = patientCompartmentFromPatientId( owner, createdInPartitionName ) ;
				compartmentsOfCreatedResource.add( DetailsImpl.compartmentContainingCreatedResource( fullyQualifiedOwner ) ) ;
			}
		}

		// return early if the resource is not in a Patient compartment
		if ( compartmentsOfCreatedResource.isEmpty() )
			return Optional.empty();


		Details details = new DetailsImpl( createdResource, createdInPartitionName, compartmentsOfCreatedResource );
		return Optional.of( details ) ;
	}

	private static boolean isResourceWrite( RequestDetails theRequestDetails ) {
		switch ( theRequestDetails.getRestOperationType() ) {
		case CREATE:
			return true ;
		case UPDATE:
			return true ;
		default:
			return false ;
		}
	}

	private IIdType patientCompartmentFromPatientId( IIdType patientId, String defaultPartitionName ) {
		if ( !"Patient".equals(patientId.getResourceType()) )
			throw new IllegalArgumentException( "expected a patient resource id but encountered a \"" + patientId.getResourceType() + "\"" ) ;
		/*
		 * TODO: compartment must include base url if it is a foreign patient
		 */
		if ( !patientId.hasBaseUrl() ) {
			return patientId.withServerBase( defaultPartitionName, "Patient" );
		} else {
			return patientId;
		}
	}
	
	private Optional<IIdType> patientCompartmentOfCreatedPatientResource( IBaseResource patientResource, String createdInPartitionName ) {
		IIdType createdPatientId = patientResource.getIdElement() ;
		if ( null == createdPatientId )
			return Optional.empty() ;
		// NOTE: #getIdElement() creates the Id if it's not already present
		if ( !createdPatientId.hasIdPart() )
			return Optional.empty() ;
		
		return Optional.of( patientCompartmentFromPatientId(createdPatientId, createdInPartitionName) ) ;
	}

	private static class DetailsImpl implements Details {

		public static CompartmentDetails compartmentOwnedByCreatedResource( Optional<IIdType> patientId ) {
			return new CompartmentDetailsImpl( true, patientId ) ;
		}
		
		public static CompartmentDetails compartmentContainingCreatedResource( IIdType patientCompartment ) {
			return new CompartmentDetailsImpl( false, Optional.of(patientCompartment) ) ;
		}
		
		private final String resourceType ;
		private final Optional<IIdType> qualifiedResourceId ;
		private final String partitionName ;
		private final Set<CompartmentDetails> compartmentDetails = new HashSet<>() ;
		private final Set<IIdType> compartments = FhirResourceComparison.idTypes().createSet() ;

		public DetailsImpl( IBaseResource createdResource, String createdInPartitionName, Set<CompartmentDetails> compartmentDetails ) {
			if ( compartmentDetails.isEmpty() )
				throw new IllegalArgumentException( "cannot initialize Patient Compartment Resource Creation details; resource does not belong to a compartment" ) ;
			this.resourceType = createdResource.fhirType() ;
			this.partitionName = createdInPartitionName ;
			this.compartmentDetails.addAll( compartmentDetails );
			compartmentDetails.stream()
				.map( CompartmentDetails::owner )
				.filter( Optional::isPresent )
				.map( Optional::get )
				.forEach( compartments::add )
				;

			IIdType resourceId = createdResource.getIdElement();
			if ( !resourceId.hasIdPart() ) {
				/*
				 * IF the resource does not have an id yet
				 * THEN the qualified id is not yet known
				 */
				this.qualifiedResourceId = Optional.empty() ;
			} else if ( !resourceId.hasResourceType() ) {
				/*
				 * IF the resource has an id BUT does not have a resource type
				 * THEN this object cannot be initialized
				 */
				 throw new IllegalArgumentException( "cannot initialize Patient Compartment Resource Creation details; resource does not have a resource type" ) ;
			} else if ( resourceId.hasBaseUrl() ) {
				/*
				 * IF the resource has an id and resource type
				 * BUT and it has base url
				 * THEN the base url must match the partition name in which it's created
				 */
				String compartmentNameFromResourceId = resourceId.getBaseUrl() ;
				if ( createdInPartitionName.equals( compartmentNameFromResourceId ) )
					throw new IllegalArgumentException( "cannot initialize Patient Compartment Resource Creation details; partition name from resource does not match storage partition name" ) ;
				this.qualifiedResourceId = Optional.of( resourceId ) ;
			} else {
				/*
				 * IF the resource has an id and resource type
				 * BUT does not have a base url
				 * THEN the fully-qualified resource id can be built from the partition name
				 */
				this.qualifiedResourceId = Optional.of( resourceId.withServerBase( createdInPartitionName, resourceId.getResourceType() ) ) ;
			}
		}

		@Override
		public boolean inherentlyClaimsCompartment(IIdType patientCompartment) {
			return
				compartmentDetails.stream()
					.filter( d -> d.owner().isPresent() )
					.filter( d -> 0 == FhirResourceComparison.idTypes().comparator().compare( d.owner().get(), patientCompartment ) )
					.filter( CompartmentDetails::ownerIsCreatedResource )
					.findFirst()
					.isPresent()
					;
		}

		@Override
		public String resourceType() {
			return this.resourceType ;
		}

		@Override
		public Optional<IIdType> qualifiedResourceId() {
			return this.qualifiedResourceId ;
		}
		
		@Override
		public String partitionName() {
			return this.partitionName ;
		}

		@Override
		public Set<CompartmentDetails> compartmentDetails() {
			return this.compartmentDetails ;
		}

		@Override
		public Set<IIdType> compartments() {
			return this.compartments ;
		}
		
		@Override
		public String toString() {
			String format = "%1$s\n%2$s in %3$s\nbelonging to %4$s" ;
			return String.format(
					format,
					qualifiedResourceId.map( id -> id.getIdPart() + " (" + id.getBaseUrl() + ")" ).orElse("[resource id not yet specified]"),
					resourceType,
					partitionName,
					compartments
				);
		}

	}

	private static class CompartmentDetailsImpl implements CompartmentDetails {
		private final boolean createdResourceOwnsCompartment ;
		private final Optional<IIdType> compartmentOwner ;
		
		private CompartmentDetailsImpl( boolean createdResourceOwnsCompartment, Optional<IIdType> compartmentOwner ) {
			if ( !createdResourceOwnsCompartment ) {
				/*
				 * IF the compartment is owned by a patient other than the created resource,
				 * THEN the owner must be fully specified
				 */
				if ( compartmentOwner.isEmpty() )
					throw new IllegalArgumentException( "cannot identify compartment; compartment owner is missing" ) ;
			}

			/*
			 * IF the compartment owner is identified
			 * THEN the owner must be fully specified
			 * AND the owner must be a Patient
			 */
			compartmentOwner.ifPresent( id -> {
				if ( !id.hasBaseUrl() )
					throw new IllegalArgumentException( "expected a fully qualified compartment owner but the base url is not specified" ) ;
				if ( !id.hasResourceType() || !id.hasIdPart() )
					throw new IllegalArgumentException( "expected a fully qualified compartment owner but the resource type is not specified" ) ;
				if ( !id.hasIdPart() )
					throw new IllegalArgumentException( "expected a fully qualified compartment owner but the resource id is not specified" ) ;
				if ( !"Patient".equals(id.getResourceType()) )
					throw new IllegalArgumentException( "expected a compartment owner that is a Patient but encountered a " + id.getResourceType() ) ;
			});

			this.createdResourceOwnsCompartment = createdResourceOwnsCompartment ;
			this.compartmentOwner = compartmentOwner ;
		}

		public boolean ownerIsCreatedResource() {
			return createdResourceOwnsCompartment;
		}

		public Optional<IIdType> owner() {
			return compartmentOwner;
		}

		@Override
		public int hashCode() {
			return Objects.hash(compartmentOwner, createdResourceOwnsCompartment);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof DetailsImpl))
				return false;
			CompartmentDetailsImpl other = (CompartmentDetailsImpl) obj;
			if ( createdResourceOwnsCompartment != other.createdResourceOwnsCompartment )
				return false ;
			if ( this.compartmentOwner.isPresent() ) {
				if ( !other.compartmentOwner.isPresent() )
					return false ;
				if (  0 != FhirResourceComparison.idTypes().comparator().compare( this.compartmentOwner.get(), other.compartmentOwner.get() ) )
					return false ;
			}
			return this.compartmentOwner.isPresent() == other.compartmentOwner.isPresent() ;
		}

		@Override
		public String toString() {
			if ( compartmentOwner.isPresent() ) {
				IIdType c = compartmentOwner.get();
				return c.getIdPart() + " (" + c.getBaseUrl() + ")" + (createdResourceOwnsCompartment ? " [owned by created resource]" : "") ;
			} else if ( createdResourceOwnsCompartment ) {
				return "[compartment owned by created resource]" ;
			} else {
				return "?missing compartment owner?" ;
			}
		}
	}
	

}
