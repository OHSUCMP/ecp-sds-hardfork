package edu.ohsu.cmp.ecp.sds;

import static edu.ohsu.cmp.ecp.sds.SupplementalDataStorePermissionsInterceptor.getPermissions;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;

@Interceptor
@Component
public class SupplementalDataStoreLinkingInterceptor {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SupplementalDataStoreLinkingInterceptor.class);

	@Inject
	SupplementalDataStoreLinkage linkage;

	@Inject
	SupplementalDataStorePartition partition;

	@Inject
	SupplementalDataStoreResourceCreation resourceCreation;

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	private Comparator<IIdType> idComparator = FhirResourceComparison.idTypes().comparator();

	@Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_CREATED)
	public void linkNewResourceToAuthorizedUser(RequestDetails theRequestDetails, RequestPartitionId requestPartitionId) {
		Permissions permissions = getPermissions( theRequestDetails );
		permissions.readAndWriteSpecificPatient()
			.ifPresent( readAndWriteSpecificPatient -> {

					resourceCreation.resourceCreationInfo( theRequestDetails )
						.ifPresent( details -> {
							PatientCompartmentLinkingPlan plan = buildLinkingPlan( readAndWriteSpecificPatient, details ) ;
							PatientCompartmentLinkingContext linkingContext = initializeLinkingContext( plan, details ) ;
							plan.linkCompartments( linkingContext ) ;
						});
				});

	}

	private PatientCompartmentLinkingPlan buildLinkingPlan( Permissions.ReadAndWriteSpecificPatient readAndWriteSpecificPatient, SupplementalDataStoreResourceCreation.Details details ) {

		PatientCompartmentLinkingPlanImpl plan = new PatientCompartmentLinkingPlanImpl( readAndWriteSpecificPatient.patientId().basisUserId() ) ;

		/*
		 * for each compartment of the created resource, require it in the plan
		 */
		for ( IIdType patientCompartment : details.compartments() ) {
			plan.requireCompartment( patientCompartment ) ;
		}

		return plan ;
	}

	private PatientCompartmentLinkingContext initializeLinkingContext( PatientCompartmentLinkingPlan plan, SupplementalDataStoreResourceCreation.Details details ) {

		PatientCompartmentLinkingContext linkingContext = new PatientCompartmentLinkingContext( plan.basisCompartment() ) ;

		/* 
		 * for each compartment that is required by the plan,
		 * if it created by this request or already exists, identify it in the context
		 */
		for ( IIdType patientCompartment : plan.requiredCompartments() ) {
			if ( details.inherentlyClaimsCompartment( patientCompartment ) ) {
				/* this request creates the compartment owner */
				linkingContext.compartmentAlreadyExists(patientCompartment) ;
			}

			if ( linkage.patientCompartmentIsClaimed(patientCompartment) ) {
				/* this compartment is already claimed */
				linkingContext.compartmentAlreadyExists(patientCompartment) ;
			}
		}

		/*
		 * if the local user already exists, identify it in the context
		 */
		linkage.lookupLocalUserFor( plan.basisCompartment() ).ifPresent( id -> {
			linkingContext.localCompartmentAlreadyExists( id ) ;
		});

		/* 
		 * for each compartment that is linked to the local patient (if any), identify it in the context
		 */
		linkingContext.localPatientId().ifPresent( localUserId -> {
			for ( IBaseReference nonLocalPatientRef : linkage.patientsLinkedTo(localUserId) ) {
				IIdType nonLocalPatientId = nonLocalPatientRef.getReferenceElement() ;
				linkingContext.nonLocalCompartmentIsAlreadyLinked( nonLocalPatientId ) ;
			}
		});

		return linkingContext ;
	}

	private interface PatientCompartmentLinkingPlan {

		IIdType basisCompartment() ;
		Set<IIdType> requiredCompartments() ;

		PatientCompartmentLinkingPlan linkCompartments( PatientCompartmentLinkingContext linkingContext ) ;

	}

	private class PatientCompartmentLinkingPlanImpl implements PatientCompartmentLinkingPlan {

		private final IIdType basisCompartment ;
		private Set<IIdType> requiredCompartments = FhirResourceComparison.idTypes().createSet();

		public PatientCompartmentLinkingPlanImpl( IIdType basisCompartment ) {
			if ( !partition.userIsNonLocal(basisCompartment))
				throw new UnsupportedOperationException( "cannot identify basis compartment as \"" + basisCompartment + "\" because it is local; basis compartments that are local is not yet supported" ) ;
			this.basisCompartment = basisCompartment ;
			this.requiredCompartments.add( basisCompartment ) ;
		}

		public IIdType basisCompartment() {
			return basisCompartment ;
		}

		public PatientCompartmentLinkingPlan requireCompartment( IIdType patientCompartment ) {
			requiredCompartments.add( patientCompartment ) ;
			return this ;
		}

		public Set<IIdType> requiredCompartments() {
			return requiredCompartments ;
		}

		private IIdType requireClaimedBasisCompartment( PatientCompartmentLinkingContext linkingContext ) {
			/* claim it and update the context */
			if ( linkingContext.nonLocalCompartments().contains( basisCompartment ) ) {
				return basisCompartment ;
			} else {
				IIdType newlyCreatedPatientCompartment = linkage.establishNonLocalUser( basisCompartment ) ;
				linkingContext.nonLocalCompartmentAlreadyExists( newlyCreatedPatientCompartment ) ;
				return newlyCreatedPatientCompartment ;
			}
		}

		private IIdType requireClaimedLocalCompartment( PatientCompartmentLinkingContext linkingContext ) {
			/* claim it and update the context */
			if ( linkingContext.localPatientId().isPresent() ) {
				return linkingContext.localPatientId().get() ;
			} else {
				IIdType newlyCreatedPatientCompartment = linkage.establishLocalUser( basisCompartment.getResourceType() ) ;
				linkingContext.localCompartmentAlreadyExists(newlyCreatedPatientCompartment) ;
				return newlyCreatedPatientCompartment ;
			}
		}

		private IIdType requireClaimedNonLocalCompartment( IIdType nonLocalCompartment, PatientCompartmentLinkingContext linkingContext ) {
			/* claim it and update the context */
			if ( linkingContext.nonLocalCompartments().contains( nonLocalCompartment ) ) {
				return nonLocalCompartment ;
			} else {
				IIdType newlyCreatedPatientCompartment = linkage.establishNonLocalUser( nonLocalCompartment ) ;
				linkingContext.nonLocalCompartmentAlreadyExists( newlyCreatedPatientCompartment ) ;
				return newlyCreatedPatientCompartment ;
			}
		}

		private void linkBasisCompartment( PatientCompartmentLinkingContext linkingContext ) {
			/* link it and update the context */
			// does not need linking; if a link is needed
			//   between the basis and local user,
			//   then #linkLocalCompartment() will do it
			requireClaimedBasisCompartment( linkingContext ) ;
		}

		private void linkLocalCompartment( PatientCompartmentLinkingContext linkingContext ) {
			/* link it and update the context */
			if ( !linkingContext.linkedNonLocalCompartments().contains( basisCompartment ) ) {
				linkage.linkNonLocalPatientToLocalPatient(
					requireClaimedLocalCompartment( linkingContext ),
					requireClaimedBasisCompartment( linkingContext )
					);
				linkingContext.basisCompartmentIsAlreadyLinked() ;
			}
		}
		
		private void linkNonLocalCompartment( IIdType nonLocalCompartment, PatientCompartmentLinkingContext linkingContext ) {
			/* link it and update the context */
			if ( !linkingContext.linkedNonLocalCompartments().contains( nonLocalCompartment ) ) {
				linkage.linkNonLocalPatientToLocalPatient(
					requireClaimedLocalCompartment( linkingContext ),
					requireClaimedNonLocalCompartment( nonLocalCompartment, linkingContext )
					);
				linkingContext.nonLocalCompartmentIsAlreadyLinked( nonLocalCompartment ) ;
			}
		}

		public PatientCompartmentLinkingPlanImpl linkCompartments( PatientCompartmentLinkingContext linkingContext ) {
			for ( IIdType requiredCompartment : requiredCompartments ) {
				if ( idsSame( requiredCompartment, basisCompartment ) ) {
					linkBasisCompartment( linkingContext ) ;
				} else if ( partition.userIsLocal(requiredCompartment) ) {
					linkBasisCompartment( linkingContext ) ;
					linkLocalCompartment( linkingContext ) ;
				} else {
					linkBasisCompartment( linkingContext ) ;
					linkLocalCompartment( linkingContext ) ;
					linkNonLocalCompartment( requiredCompartment, linkingContext );
				}
			}
			return this ;
		}

	}

	private class PatientCompartmentLinkingContext {
		
		private final IIdType basisCompartment ;
		private Optional<IIdType> localPatientId = Optional.empty();
		private Set<IIdType> nonLocalCompartments = FhirResourceComparison.idTypes().createSet();
		private Set<IIdType> linkedNonLocalCompartments = FhirResourceComparison.idTypes().createSet();
	
		public Optional<IIdType> localPatientId() {
			return localPatientId ;
		}
		
		public IIdType basisCompartment() {
			return basisCompartment ;
		}
		
		public Set<IIdType> nonLocalCompartments() {
			return nonLocalCompartments ;
		}
		
		public Set<IIdType> linkedNonLocalCompartments() {
			return linkedNonLocalCompartments ;
		}
		
		public PatientCompartmentLinkingContext( IIdType basisCompartment ) {
			if ( !partition.userIsNonLocal(basisCompartment))
				throw new UnsupportedOperationException( "cannot identify basis compartment as \"" + basisCompartment + "\" because it is local; basis compartments that are local is not yet supported" ) ;
			this.basisCompartment = basisCompartment ;
		}

		PatientCompartmentLinkingContext basisCompartmentIsAlreadyLinked() {
			this.linkedNonLocalCompartments.add( basisCompartment ) ;
			return this ;
		}
		
		PatientCompartmentLinkingContext localCompartmentAlreadyExists( IIdType localPatientCompartment ) {
			if ( !partition.userIsLocal(localPatientCompartment))
				throw new IllegalArgumentException( "cannot identify local patient as \"" + localPatientCompartment + "\" because it is non-local" ) ;
			this.localPatientId.ifPresent( id -> {
				if ( idsDifferent(id, localPatientCompartment) ) {
					switch ( sdsProperties.getPartition().getMultipleLinkedLocalPatients() ) {
					case FAIL:
						throw new IllegalArgumentException( "local patient already identified as \"" + id + "\"; cannot re-identify as \"" + localPatientCompartment + "\"" ) ;
					case WARN:
						ourLog.warn( "local patient already identified as \"" + id + "\"; re-identifing as \"" + localPatientCompartment + "\" will cause problems with access permissions later" ) ;
					case IGNORE:
						ourLog.debug( "local patient already identified as \"" + id + "\"; re-identifing as \"" + localPatientCompartment + "\" will cause problems with access permissions later" ) ;
					default:
					}
				}
			});
			this.localPatientId = Optional.of( localPatientCompartment ) ;
			return this ;
		}
		
		PatientCompartmentLinkingContext nonLocalCompartmentAlreadyExists( IIdType nonLocalPatientCompartment ) {
			if ( !partition.userIsNonLocal(nonLocalPatientCompartment))
				throw new IllegalArgumentException( "cannot identify non-local compartment as \"" + nonLocalPatientCompartment + "\" because it is local" ) ;
			nonLocalCompartments.add( nonLocalPatientCompartment ) ;
			return this ;
		}

		PatientCompartmentLinkingContext compartmentAlreadyExists( IIdType patientCompartment ) {
			if ( partition.userIsLocal(patientCompartment) )
				return this.localCompartmentAlreadyExists(patientCompartment) ;
			else
				return this.nonLocalCompartmentAlreadyExists(patientCompartment) ;
		}

		PatientCompartmentLinkingContext nonLocalCompartmentIsAlreadyLinked( IIdType nonLocalPatientCompartment ) {
			if ( !nonLocalCompartments.contains( nonLocalPatientCompartment ) )
				new IllegalArgumentException( "cannot identify non-local compartment \"" + nonLocalPatientCompartment + "\" as already linked because the local compartment has not been identified yet" ) ;
			this.linkedNonLocalCompartments.add( nonLocalPatientCompartment ) ;
			return this ;
		}
	}

	private boolean idsDifferent( IIdType a, IIdType b ) {
		return 0 != idComparator.compare(a, b) ;
	}

	private boolean idsSame( IIdType a, IIdType b ) {
		return 0 == idComparator.compare(a, b) ;
	}
}
