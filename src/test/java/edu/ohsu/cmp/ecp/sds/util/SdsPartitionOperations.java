package edu.ohsu.cmp.ecp.sds.util;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.http.client.utils.URIBuilder;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;

import ca.uhn.fhir.model.api.IQueryParameterType;
import ca.uhn.fhir.rest.api.DeleteCascadeModeEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.provider.ProviderConstants;
import edu.ohsu.cmp.ecp.sds.assertions.UpdateableOnce;
import junit.framework.AssertionFailedError;

public class SdsPartitionOperations {

	private final IGenericClient client ;
	private UpdateableOnce<IIdType> patientId ;

	private final PatientOperations patientOperations = new PatientOperations() ;

	private Optional<IdGenerationStrategy> resourceIdGenerationStrategy = Optional.empty() ;

	public SdsPartitionOperations( IGenericClient client, UpdateableOnce<IIdType> patientId ) {
		this.client = client ;
		this.patientId = patientId ;
	}

	public interface IdGenerationStrategy {
		IIdType generateId( Class<? extends IBaseResource> resourceType ) ;
	}

	public SdsPartitionOperations with( IdGenerationStrategy idGenerationStrategy ) {
		this.resourceIdGenerationStrategy = Optional.of( idGenerationStrategy ) ;
		return this ;
	}

	public UpdateableOnce<IIdType> id() {
		return patientId ;
	}

	public PatientOperations patient() {
		return this.patientOperations ;
	}

	public <T extends IBaseResource> ResourceOperations<T> resources( Class<T> resourceType) {
		return new ResourceOperations<>(resourceType).with( resourceIdGenerationStrategy ) ;
	}

	public class PatientOperations {

		public Patient read() {
			return SdsPartitionOperations.this.read( Patient.class, patientId.orElseThrow( IllegalStateException.class ) ) ;
		}

		public IIdType create() {
			return create( (patient) -> {} ) ;
		}

		public IIdType create( Consumer<Patient> configurer ) {
			return SdsPartitionOperations.this.create(
				Patient.class,
				(p) -> {
					patientId.value().ifPresent( p::setId ) ;
					configurer.accept(p);
					}
				);
		}

		public MethodOutcome deleteCascade() {
			return SdsPartitionOperations.this.deleteCascade( patientId.orElseThrow( IllegalStateException.class ) ) ;
		}

		public MethodOutcome deleteWithExpunge() {
			return SdsPartitionOperations.this.deleteWithExpunge( patientId.orElseThrow( IllegalStateException.class ) ) ;
		}
		
		public IBaseParameters expungeOperation() {
			return SdsPartitionOperations.this.expungeOperation( patientId.orElseThrow( IllegalStateException.class ) ) ;
		}

		public IBaseParameters deleteExpungeOperation() {
			return SdsPartitionOperations.this.deleteExpungeOperation( patientId.orElseThrow( IllegalStateException.class ) ) ;
		}

	}

	public class ResourceOperations<T extends IBaseResource> {
		private final Class<T> resourceType;
		private Optional<IdGenerationStrategy> resourceIdGenerationStrategy = Optional.empty() ;

		public ResourceOperations( Class<T> resourceType ) {
			this.resourceType = resourceType ;
		}

		ResourceOperations<T> with( Optional<IdGenerationStrategy> idGenerationStrategy ) {
			this.resourceIdGenerationStrategy = idGenerationStrategy ;
			return this ;
		}

		public T read( IIdType resourceId ) {
			return SdsPartitionOperations.this.read( resourceType, resourceId ) ;
		}

		public List<T> search( ca.uhn.fhir.rest.gclient.ReferenceClientParam compartmentParameter ) {
			return SdsPartitionOperations.this.search( resourceType, compartmentParameter ) ;
		}

		public List<T> search( Function<IQuery<IBaseBundle>, IQuery<IBaseBundle>> queryConfigurer ) {
			return SdsPartitionOperations.this.search( resourceType, queryConfigurer ) ;
		}

		public List<T> search( BiFunction<SdsPartitionOperations, IQuery<IBaseBundle>, IQuery<IBaseBundle>> queryConfigurer ) {
			return SdsPartitionOperations.this.search( resourceType, queryConfigurer ) ;
		}

		private BiConsumer<SdsPartitionOperations, T> assignResourceIdAnd( IIdType resourceId, BiConsumer<SdsPartitionOperations, T> configurer ) {
			return (ops,res) -> {
				res.setId( resourceId ) ;
				configurer.accept( ops, res ) ;
			};
		}

		public IIdType create() {
			return create( (ops,res) -> {} ) ;
		}

		public IIdType create( IIdType resourceId ) {
			return create( resourceId, (ops,res) -> {} ) ;
		}

		public IIdType create( IIdType resourceId, Consumer<T> configurer ) {
			return create( resourceId, (ops,res) -> configurer.accept(res) ) ;
		}

		public IIdType create( IIdType resourceId, BiConsumer<SdsPartitionOperations, T> configurer ) {
			resourceIdGenerationStrategy.ifPresent( s -> {
				throw new AssertionFailedError( "resourceId provided for new resource, but ResourceOperations configured with " + s.getClass().getSimpleName() ) ;
			});
			return SdsPartitionOperations.this.create( resourceType, assignResourceIdAnd( resourceId, configurer ) );
		}

		public IIdType create( Consumer<T> configurer ) {
			return create( (ops,res) -> configurer.accept(res) );
		}

		public IIdType create( BiConsumer<SdsPartitionOperations, T> configurer ) {
			if ( resourceIdGenerationStrategy.isPresent() ) {
				IIdType resourceId = resourceIdGenerationStrategy.get().generateId(resourceType) ;
				return SdsPartitionOperations.this.create( resourceType, assignResourceIdAnd( resourceId, configurer ) );
			} else {
				return SdsPartitionOperations.this.create( resourceType, configurer );
			}
		}
	
		public MethodOutcome deleteCascade( IIdType resourceId ) {
			return SdsPartitionOperations.this.deleteCascade( resourceId ) ;
		}

		public MethodOutcome deleteWithExpunge( IIdType resourceId ) {
			return SdsPartitionOperations.this.deleteWithExpunge( resourceId ) ;
		}
		
		public IBaseParameters expungeOperation( IIdType resourceId ) {
			return SdsPartitionOperations.this.expungeOperation( resourceId ) ;
		}

		public IBaseParameters deleteExpungeOperation( IIdType resourceId ) {
			return SdsPartitionOperations.this.deleteExpungeOperation( resourceId ) ;
		}

	}

	public <T extends IBaseResource> IIdType create( Class<T> resourceType ) {
		return create( resourceType, (r) -> {} ) ;
	}

	public <T extends IBaseResource> IIdType create( Class<T> resourceType, Consumer<T> configurer ) {
		return create( resourceType, (ops,res) -> configurer.accept(res) ) ;
	}

	public <T extends IBaseResource> IIdType create( Class<T> resourceType, BiConsumer<SdsPartitionOperations, T> configurer ) {
		try {
			Constructor<T> ctor = resourceType.getDeclaredConstructor() ;
			T instance = ctor.newInstance() ;
			return create( instance, configurer ) ;
		} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
			throw new IllegalArgumentException( "failed to construct an instance of " + resourceType.getName(), ex ) ;
		}
	}

	public <T extends IBaseResource> IIdType create( T resource ) {
		return create( resource, (r) -> {} ) ;
	}

	public <T extends IBaseResource> IIdType create( T resource, Consumer<T> configurer ) {
		return create( resource, (ops,res) -> configurer.accept(res) ) ;
	}
	
	public <T extends IBaseResource> IIdType create( T resource, BiConsumer<SdsPartitionOperations, T> configurer ) {
		configurer.accept(this, resource);
		MethodOutcome methodOutcome ;
		if ( null != resource.getIdElement() && null != resource.getIdElement().getIdPart() ) {
			methodOutcome = client.update().resource( resource ).execute();
		} else {
			methodOutcome = client.create().resource( resource ).execute();
		}
		IIdType newlyCreatedResourceId = methodOutcome.getId().toUnqualifiedVersionless();

		if ( "Patient".equalsIgnoreCase( resource.fhirType() ) )
			patientId.update( newlyCreatedResourceId ) ;
		return newlyCreatedResourceId;
	}

	public <T extends IBaseResource> T read( Class<T> resourceType, IIdType resourceId ) {
		return client.read().resource( resourceType ).withId( resourceId ).execute() ;
	}

	public <T extends IBaseResource> List<T> search( Class<T> resourceType, ca.uhn.fhir.rest.gclient.ReferenceClientParam compartmentParameter ) {
		return search( resourceType, (ops,query) -> query.where( compartmentParameter.hasId( ops.id().orElseThrow( IllegalStateException.class ).toUnqualifiedVersionless() ) ) ) ;
	}

	public <T extends IBaseResource> List<T> search( Class<T> resourceType, Function<IQuery<IBaseBundle>, IQuery<IBaseBundle>> queryConfigurer ) {
		return search( resourceType, (ops,query) -> queryConfigurer.apply(query) ) ;
	}

	public <T extends IBaseResource> List<T> search( Class<T> resourceType, BiFunction<SdsPartitionOperations, IQuery<IBaseBundle>, IQuery<IBaseBundle>> queryConfigurer ) {
		IQuery<IBaseBundle> query = client.search().forResource( resourceType );
		IQuery<IBaseBundle> configuredQuery = queryConfigurer.apply( this, query ) ;
		Bundle bundle = configuredQuery.returnBundle( Bundle.class ).execute() ;
		return unpackBundle( bundle ) ;
	}

	private <T extends IBaseResource> List<T> unpackBundle( Bundle bundle ) {
		return
			bundle.getEntry().stream()
				.filter( Bundle.BundleEntryComponent::hasSearch )
				.filter( e -> e.getSearch().hasMode() && Bundle.SearchEntryMode.MATCH == e.getSearch().getMode() )
				.filter( Bundle.BundleEntryComponent::hasResource )
				.map( Bundle.BundleEntryComponent::getResource )
				.map( r -> (T)r )
				.collect( toList() )
				;
	}

	private static final Map<String,List<IQueryParameterType>> EXPUNGE_QUERY_PARAMETER = singletonMap( "_expunge", asList( (IQueryParameterType) new StringParam( "true" ) ) ) ;

	/*
	 * https://smilecdr.com/docs/fhir_repository/deleting_data.html#cascading-deletes
	 * e.g. DELETE /Patient/123?_cascade=delete
	 */
	public MethodOutcome deleteCascade( IIdType resourceId ) {
		MethodOutcome methodOutcome =
			client
				.delete()
				.resourceById( resourceId )
				.cascade( DeleteCascadeModeEnum.DELETE )
				.execute()
				;
		assertThat( methodOutcome.getId(), notNullValue() ) ;
		assertNoProblemIssues( methodOutcome ) ;
		printIssues( methodOutcome ) ;
		return methodOutcome ;
	}

	/*
	 * https://smilecdr.com/docs/fhir_repository/deleting_data.html#delete-expunge-delete-with-expungetrue
	 * e.g. DELETE [base]/Observation?status=cancelled&_expunge=true
	 * e.g. DELETE [base]/Patient?_id=123&_expunge=true
	 */
	public MethodOutcome deleteWithExpunge( IIdType resourceId ) {
		MethodOutcome methodOutcome =
			client
				.delete()
					.resourceConditionalByType( resourceId.getResourceType() )
					.where( IAnyResource.RES_ID.exactly().identifier( resourceId.getIdPart() ) )
					.where( EXPUNGE_QUERY_PARAMETER )
					.cascade( DeleteCascadeModeEnum.DELETE )
					.execute()
					;
//		assertThat( methodOutcome.getId(), notNullValue() ) ;
		assertNoProblemIssues( methodOutcome ) ;
		printIssues( methodOutcome ) ;
		return methodOutcome ;
	}

	/*
	 * https://smilecdr.com/docs/fhir_repository/deleting_data.html#expunge
	 * e.g. POST [base]/Patient/123/$expunge ( expungeDeletedResources=true, expungePreviousVersions=true)
	 */
	public IBaseParameters expungeOperation( IIdType resourceId ) {
		IBaseParameters operationResult =
			client
				.operation()
				.onInstance( resourceId )
				.named( ProviderConstants.OPERATION_EXPUNGE )
				.withParameters(
					new Parameters()
						.addParameter( "expungeEverything", new BooleanType(true) )
						.addParameter( "expungeDeletedResources", new BooleanType(true) )
						.addParameter( "expungePreviousVersions", new BooleanType(true) )
						.addParameter( "_cascade", new StringType("delete") )
					)
				.execute()
				;
//		assertThat( methodOutcome.getId(), notNullValue() ) ;
//		assertNoProblemIssues( methodOutcome ) ;
//		printIssues( methodOutcome ) ;
		return operationResult ;
	}

	/*
	 * https://smilecdr.com/docs/fhir_repository/deleting_data.html#delete-expunge-delete-expunge-operation
	 * e.g. POST [base]/$delete-expunge ( url=Patient?_id=abc123, _cascade=true)
	 */
	public IBaseParameters deleteExpungeOperation( IIdType resourceId ) {
		URI deleteQueryUrl ;
		try {
			deleteQueryUrl =
				new URIBuilder()
					.setPath( resourceId.getResourceType() )
					.addParameter( "_id", resourceId.getIdPart() )
					.build()
					;
		} catch (URISyntaxException ex) {
			throw new IllegalArgumentException( "cannot build url for expunge-delete", ex ) ;
		}
		IBaseParameters operationResult =
			client
				.operation()
				.onServer()
				.named( ProviderConstants.OPERATION_DELETE_EXPUNGE )
				.withParameters(
					new Parameters()
						.addParameter( ProviderConstants.OPERATION_DELETE_EXPUNGE_URL, new StringType( deleteQueryUrl.toString().replaceAll("^/","") ) )
						.addParameter( ProviderConstants.OPERATION_DELETE_CASCADE, new BooleanType(true) )
						)
				.execute()
				;
//		assertThat( methodOutcome.getId(), notNullValue() ) ;
//		assertNoProblemIssues( methodOutcome ) ;
//		printIssues( methodOutcome ) ;
		return operationResult ;
	}

	private void assertNoProblemIssues( MethodOutcome methodOutcome ) {
		IBaseOperationOutcome operationOutcome = methodOutcome.getOperationOutcome() ;
		if ( null != operationOutcome )
			assertNoProblemIssues( (OperationOutcome)operationOutcome ) ;
	}

	private void assertNoProblemIssues( OperationOutcome operationOutcome ) {
		if ( operationOutcome.hasIssue() ) {
			List<OperationOutcomeIssueComponent> problemIssues =
				operationOutcome.getIssue().stream()
					.filter( i -> {
						switch ( i.getSeverity() ) {
						case INFORMATION:
							return false ;
						default:
							return true ;
						}
					})
					.collect( toList() );
			assertThat( problemIssues, empty() );
		}
	}

	private void printIssues( MethodOutcome methodOutcome ) {
		IBaseOperationOutcome operationOutcome = methodOutcome.getOperationOutcome() ;
		if ( null != operationOutcome )
			printIssues( (OperationOutcome)operationOutcome ) ;
	}

	private void printIssues( OperationOutcome operationOutcome ) {
		if ( operationOutcome.hasIssue() ) {
			
			for ( OperationOutcomeIssueComponent issue : operationOutcome.getIssue() ) {
				StringBuilder sb = new StringBuilder() ;
				sb.append( "<" ) ;
				sb.append( issue.getSeverity() ) ;
				sb.append( ">" ) ;
				sb.append( " " ) ;
				sb.append( issue.getCode() ) ;
				sb.append( "" ) ;
				CodeableConcept details = issue.getDetails() ;
				for ( Coding coding : details.getCoding() ) {
					sb.append( " " ) ;
					if ( coding.hasDisplay() ) {
						sb.append( coding.getDisplay() ) ;
					} else if ( coding.hasCode() ) {
						sb.append( " " ) ;
						sb.append( coding.getCode() ) ;
						if ( coding.hasSystem() ) {
							sb.append( " (" ) ;
							sb.append( coding.getCode() ) ;
							sb.append( ")" ) ;
						}
					}
				}
				String diagnostics = issue.getDiagnostics() ;
				if ( null != diagnostics ) {
					sb.append( " " ) ;
					sb.append( diagnostics ) ;
				}
				System.out.println( sb.toString() ) ;
			}
		}
	}

}
