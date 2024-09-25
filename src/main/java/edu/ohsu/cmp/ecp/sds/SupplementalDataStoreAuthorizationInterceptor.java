package edu.ohsu.cmp.ecp.sds;

import static edu.ohsu.cmp.ecp.sds.SupplementalDataStorePermissionsInterceptor.getPermissions;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilder;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;
import ca.uhn.fhir.rest.server.provider.ProviderConstants;

@Interceptor
@Component
public class SupplementalDataStoreAuthorizationInterceptor extends AuthorizationInterceptor {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SupplementalDataStoreAuthorizationInterceptor.class);

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	SupplementalDataStoreAuth auth;

	private static IAuthRuleBuilder ruleBuilder() {
		return new RuleBuilder();
	}

	@Override
	public List<IAuthRule> buildRuleList(RequestDetails theRequestDetails) {
		List<IAuthRule> rules = new ArrayList<>() ;

		ruleBuilder()
			.allow( "capability statement" )
			.metadata()
			.build()
			.forEach( rules::add );
			;

		Permissions permissions = getPermissions(theRequestDetails);
		buildRuleListForPermissions( permissions ).forEach( rules::add );

		ruleBuilder()
			.denyAll("no access rules grant permission")
			.build()
			.forEach( rules::add  )
			;

		return rules ;
	}

	private List<IAuthRule> buildRuleListForPermissions(Permissions permissions) {

		if ( null == permissions ) {
			/* return early, no details of the authorization are available */
			return ruleBuilder()
				.denyAll("expected user to be authorized for patient read and/or write")
				.build();
		}

		if ( permissions.readSpecificPatient().isPresent() )
			return buildRuleListForPermissions( permissions.readSpecificPatient().get() ) ;

		if ( permissions.readAllPatients().isPresent() )
			return buildRuleListForPermissions( permissions.readAllPatients().get() ) ;

		if ( permissions.readAndWriteSpecificPatient().isPresent() )
			return buildRuleListForPermissions( permissions.readAndWriteSpecificPatient().get() ) ;

		/* no details of the patient identity are available */
		return ruleBuilder()
			.denyAll("expected user \"" + permissions.authorizedUserId() + "\" to be authorized for patient read and/or write")
			.build();
	}

	private List<IAuthRule> buildRuleListForPermissions( Permissions.ReadAllPatients readAllPatients ) {
		List<IAuthRule> rules = new ArrayList<>() ;
		/* permit access to all sds-foreign records for any patient in each partition */
		ruleBuilder()
			.allow( describePatientPermission( "read", false ) )
			.read()
			.allResources()
			.withAnyId()
			.build().forEach( rules::add )
		;

		/* permit access to all sds-local linkages */
		ruleBuilder()
			.allow( describePatientPermission( "read linkages for", false ) )
			.read()
			.resourcesOfType("Linkage")
			.withAnyId()
			.build().forEach( rules::add )
			;

		return rules ;
	}

	private List<IAuthRule> buildRuleListForPermissions( Permissions.ReadSpecificPatient readSpecificPatient ) {
		List<IAuthRule> rules = new ArrayList<>() ;

		readSpecificPatient.patientId().localUserId().ifPresent( localPatientId -> {

			/* permit access to all sds-local records for specific patient */
			inspectPatientCompartment( true, localPatientId )
				.forEach( rules::add );
			if ( localPatientId.hasBaseUrl() ) {
				inspectPatientCompartment( true, localPatientId.toUnqualifiedVersionless() )
					.forEach( rules::add );
			}
			/* permit access to all sds-local linkages that link to specific patient */
			inspectLinkages( true, localPatientId.toUnqualifiedVersionless() )
				.forEach( rules::add ) ;

		});

		/* permit access to all sds-foreign records for specific patient in each partition */
		for (IIdType nonLocalPatientId : readSpecificPatient.patientId().nonLocalUserIds() ) {
			inspectPatientCompartment( false, nonLocalPatientId )
				.forEach( rules::add ) ;
			if ( nonLocalPatientId.hasBaseUrl() )
				inspectPatientCompartment( false, nonLocalPatientId.toUnqualifiedVersionless() )
					.forEach( rules::add ) ;

			/* permit access to all sds-foreign linkages that link to specific patient */
			inspectLinkages( false, nonLocalPatientId )
				.forEach( rules::add ) ;
		}

		return rules ;
	}

	private List<IAuthRule> buildRuleListForPermissions( Permissions.ReadAndWriteSpecificPatient readAndWriteSpecificPatients ) {
		List<IAuthRule> rules = new ArrayList<>() ;

		if ( !readAndWriteSpecificPatients.patientId().localUserId().isPresent() ) {

			/* permit creating a new patient */
			ruleBuilder()
				.allow( describePatientPermission("create", true, null) )
				.create().resourcesOfType("Patient").withAnyId()
				.build().forEach( rules::add )
				;

		}

		readAndWriteSpecificPatients.patientId().localUserId().ifPresent( localPatientId -> {

			/* permit access to all sds-local records for specific patient */
			managePatientCompartment( true, localPatientId )
				.forEach( rules::add );
			if ( localPatientId.hasBaseUrl() ) {
				managePatientCompartment( true, localPatientId.toUnqualifiedVersionless() )
					.forEach( rules::add );
			}
			/* permit access to all sds-local linkages that link to specific patient */
			manageLinkages( true, localPatientId.toUnqualifiedVersionless() )
				.forEach( rules::add ) ;

		});

		/* permit access to all sds-foreign records for specific patient in each partition */
		for (IIdType nonLocalPatientId : readAndWriteSpecificPatients.patientId().nonLocalUserIds() ) {
			managePatientCompartment( false, nonLocalPatientId )
				.forEach( rules::add ) ;
			if ( nonLocalPatientId.hasBaseUrl() )
				managePatientCompartment( false, nonLocalPatientId.toUnqualifiedVersionless() )
					.forEach( rules::add ) ;

			/* permit access to all sds-foreign linkages that link to specific patient */
			manageLinkages( false, nonLocalPatientId )
				.forEach( rules::add ) ;
		}

		return rules;
	}

	private String describePatientPermission( String operation, boolean isLocal ) {
		String patientRelatedOperationDesc =
			String.format(
					"%1$s any %2$s patient",
					operation,
					isLocal ? "local" : "non-local"
				);
		return patientRelatedOperationDesc ;
	}

	private String describePatientPermission( String operation, boolean isLocal, IIdType patientId ) {
		String patientRelatedOperationDesc =
				String.format(
					"%1$s %2$s patient %3$s",
					operation,
					isLocal ? "local" : "non-local",
					null != patientId ? patientId : "-new-"
				);
		return patientRelatedOperationDesc ;
	}

	private List<IAuthRule> managePatientCompartment( boolean isLocal, IIdType patientId ) {
		List<IAuthRule> rules = new ArrayList<>() ;

		ruleBuilder()
			.allow( describePatientPermission("read", isLocal, patientId) )
			.read().allResources().inCompartment("Patient", patientId)
			.andThen()
			.allow( describePatientPermission("write", isLocal, patientId) )
			.write().allResources().inCompartment("Patient", patientId)
			.andThen()
			.allow( describePatientPermission("delete", isLocal, patientId) )
			.delete().allResources().inCompartment("Patient", patientId)
			.build()
			.forEach( rules::add )
			;

		/* rule builder tries to collapse compartment rules,
		 *  but collapses delete + cascade-delete & expunge-delete into
		 *  just one without regard for the cascade or expunge
		 * so, build them using separate RuleBuilder instances
		 */
		ruleBuilder()
			.allow( describePatientPermission("delete-with-cascade", isLocal, patientId) )
			.delete().onCascade().allResources().inCompartment("Patient", patientId)
			.build()
			.forEach( rules::add )
			;
		ruleBuilder()
			.allow( describePatientPermission("delete-with-expunge", isLocal, patientId) )
			.delete().onExpunge().allResources().inCompartment("Patient", patientId)
			.build()
			.forEach( rules::add )
			;
		/*
		 * $expunge SHOULD require parameter that specifies targeting "deleted" resource only
		 */
		/*
		 * Necause this should be available to clients even AFTER they've soft-deleted the
		 * Patient resources that grant them permission, using `.onInstance( patientId )` is too narrow
		 *
		 * Using `.onAnyInstance()` opens the possibility that ANY client may expunge ANY
		 * previously deleted resources.  The implication is that the SDS cannot support
		 * reliable un-delete
		 */
		ruleBuilder()
			.allow( describePatientPermission("operation $expunge", isLocal, patientId) )
			.operation().named( ProviderConstants.OPERATION_EXPUNGE ).onAnyInstance().andAllowAllResponsesWithAllResourcesAccess()
			.build()
			.forEach( rules::add )
			;

		/*
		 * $expunge SHOULD check permission for specific patients identified in the operation parameters
		 * and SHOULD require parameter that specifies targeting "deleted" resource only
		 */
		/*
		 * server-level $expunge permits DROP ALL
		 * this cannot be configured as below; it requires a custom IAuthRule
		 *
		ruleBuilder()
			.allow( describePatientPermission("operation $expunge", isLocal, patientId) )
			.operation().named( ProviderConstants.OPERATION_EXPUNGE ).onServer().andAllowAllResponsesWithAllResourcesAccess()
			.build()
			.forEach( rules::add )
			;
		*/

		/*
		 * $delete-expunge requires permission for specific patients identified in the operation parameters
		 * this cannot be configured as below; it requires a custom IAuthRule
		 *
		ruleBuilder()
			.allow( describePatientPermission("operation $delete-expunge", isLocal, patientId) )
			.operation().named( ProviderConstants.OPERATION_DELETE_EXPUNGE ).onServer().andAllowAllResponsesWithAllResourcesAccess()
			.parameter( "url" ).identifiesResource( patientId )
			.build()
			.forEach( rules::add )
			;
		*/

		return rules ;
	}

	private List<IAuthRule> inspectPatientCompartment( boolean isLocal, IIdType patientId ) {
		List<IAuthRule> rules = new ArrayList<>() ;

		ruleBuilder()
			.allow( describePatientPermission("read", isLocal, patientId) )
			.read().allResources().inCompartment("Patient", patientId)
			.build()
			.forEach( rules::add )
			;

		return rules ;
	}

	private List<IAuthRule> manageLinkages( boolean isLocal, IIdType patientId ) {
		/* omitting the resource type DOES break the Linkage search */
		String filterForLinkageByItem = "item=" + patientId.toUnqualifiedVersionless().toString();

		return ruleBuilder()
			.allow( describePatientPermission("read linkages for", isLocal, patientId) )
			.read().resourcesOfType("Linkage").withFilter( filterForLinkageByItem )
			.andThen()
			.allow( describePatientPermission("cascade-delete linkages for", isLocal, patientId) )
			.delete().onCascade().resourcesOfType("Linkage").withFilter( filterForLinkageByItem )
			.andThen()
			.allow( describePatientPermission("expunge-delete linkages for", isLocal, patientId) )
			.delete().onExpunge().resourcesOfType("Linkage").withFilter( filterForLinkageByItem )
			.build()
			;
	}

	private List<IAuthRule> inspectLinkages( boolean isLocal, IIdType patientId ) {
		/* omitting the resource type DOES break the Linkage search */
		String filterForLinkageByItem = "item=" + patientId.toUnqualifiedVersionless().toString();

		return ruleBuilder()
			.allow( describePatientPermission("read linkages for", isLocal, patientId) )
			.read().resourcesOfType("Linkage").withFilter( filterForLinkageByItem )
			.build()
			;
	}

}
