package edu.ohsu.cmp.ecp.sds;

import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreAuth.AuthorizationProfile;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreAuth.LaunchContext;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;

@Interceptor
@Component
public class SupplementalDataStorePermissionsInterceptor {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SupplementalDataStorePermissionsInterceptor.class);

	@Inject
	SupplementalDataStoreAuth auth;

	@Inject
	SupplementalDataStoreLinkage linkage;

	@Inject
	SupplementalDataStorePartition partition;

	@Inject
	SupplementalDataStoreResourceCreation resourceCreation;
	
	public static final String REQUEST_ATTR_PERMISSIONS = "SDS-AUTH-PERMISSIONS" ;

	public static Permissions getPermissions(RequestDetails theRequestDetails) {
		return (Permissions)theRequestDetails.getAttribute(REQUEST_ATTR_PERMISSIONS) ;
	}

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
	public void identifyPermissions(RequestDetails theRequestDetails) {

		/*
		 * identify the authorized user
		 * and store the ID in the RequestDetails
		 */

		AuthorizationProfile authProfile = auth.authorizationProfile(theRequestDetails);
		if ( null == authProfile )
			return ;

		IIdType authorizedUserId = authProfile.getAuthorizedUserId();

		/*
		 * identify the authorized user
		 * and store the permissions in the RequestDetails
		 */

		if ( "Practitioner".equalsIgnoreCase( authorizedUserId.getResourceType() ) ) {
			theRequestDetails.setAttribute(
				REQUEST_ATTR_PERMISSIONS,
				permissionsForPractitioner( authorizedUserId, authProfile.getLaunchContext() )
			);
		} else {
			theRequestDetails.setAttribute(
				REQUEST_ATTR_PERMISSIONS,
				permissionsForPatient( authorizedUserId, authProfile.getTargetPatientId(), theRequestDetails )
			);
		}
	}

	private UserIdentity buildUserIdentity( IIdType basisUserId ) {

		Optional<IIdType> localUserId = linkage.lookupLocalUserFor(basisUserId);

		return buildUserIdentity( localUserId, basisUserId ) ;
	}

	private UserIdentity buildUserIdentity( Optional<IIdType> localUserId, IIdType basisUserId ) {

		Set<IIdType> nonLocalPatientIds = FhirResourceComparison.idTypes().createSet() ;
		if ( partition.userIsNonLocal( basisUserId ) )
			nonLocalPatientIds.add( basisUserId );

		localUserId.ifPresent( id -> {
			for ( IBaseReference nonLocalUser : linkage.patientsLinkedTo(id) ) {
				IIdType nonLocalUserId = nonLocalUser.getReferenceElement();
				nonLocalPatientIds.add( nonLocalUserId );
			}
		});

		return new UserIdentity(basisUserId, localUserId, nonLocalPatientIds) ;
	}

	private Permissions permissionsForPractitioner( IIdType authorizedUserId, LaunchContext launchContext ) {
		if ( null != launchContext && null != launchContext.getPatient() ) {
			return permissionsForPractitionerInPatientContext(authorizedUserId, launchContext.getPatient()) ;
		} else {
			return permissionsForPractitionerWithoutContext( authorizedUserId ) ;
		}
	}

	private Permissions permissionsForPractitionerWithoutContext( IIdType authorizedUserId ) {
		return new Permissions( new Permissions.ReadAllPatients(authorizedUserId) ) ;
	}

	private Permissions permissionsForPractitionerInPatientContext( IIdType authorizedUserId, IIdType launchPatientContext ) {
		UserIdentity contextPatientId = buildUserIdentity( launchPatientContext );

		return new Permissions( new Permissions.ReadSpecificPatient(authorizedUserId, contextPatientId ) ) ;
	}

	private Permissions permissionsForPatient( IIdType authorizedUserId, IIdType authorizedPatientId, RequestDetails theRequestDetails ) {


		UserIdentity targetPatientId = buildUserIdentity( authorizedPatientId );
		/*
		 * IF the request is a resource WRITE
		 * AND the resource's Patient Compartment is not already linked to the authorized user
		 * AND the resource's Patient Compartment is empty (i.e. unclaimed)
		 * THEN flag the patient compartment for permission to create
		 */

		Optional<SupplementalDataStoreResourceCreation.Details> details = resourceCreation.resourceCreationInfo( theRequestDetails ) ;

		// only if it's a resource creation
		if ( details.isPresent() ) {
			for ( IIdType claimingPatientCompartment : claimingPatientCompartments( details.get() ) ) {
				targetPatientId = targetPatientId.withAdditionalNonLocalUserId( claimingPatientCompartment ) ;
			}
		}

		return new Permissions( new Permissions.ReadAndWriteSpecificPatient( authorizedUserId, targetPatientId ) ) ;
	}

	private Set<IIdType> claimingPatientCompartments( SupplementalDataStoreResourceCreation.Details details ) {
		Set<IIdType> claimingCompartments = FhirResourceComparison.idTypes().createSet();

		for ( IIdType patientCompartment : details.compartments() ) {
			if ( linkage.patientCompartmentIsClaimed( patientCompartment ) ) {
				ourLog.warn(
					String.format(
						"attempt to claim \"%1$s\" was prohibited because it is already claimed",
						patientCompartment
					)
				);
				continue ;
			}

			claimingCompartments.add( patientCompartment ) ;
		}

		return claimingCompartments ;
	}

}
