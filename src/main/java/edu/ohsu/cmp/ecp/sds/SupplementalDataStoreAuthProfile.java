package edu.ohsu.cmp.ecp.sds;

import org.hl7.fhir.instance.model.api.IIdType;

import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreAuth.AuthorizationProfile;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreAuth.LaunchContext;

public class SupplementalDataStoreAuthProfile implements AuthorizationProfile {

	private final IIdType authorizedUserId;
	private final IIdType targetPatientId;
	private final LaunchContext launchContext;

	public SupplementalDataStoreAuthProfile(IIdType authorizedUserId, IIdType targetPatientId, LaunchContext launchContext ) {
		this.authorizedUserId = authorizedUserId;
		this.targetPatientId = targetPatientId;
		this.launchContext = launchContext;
	}

	public static AuthorizationProfile forPatient( IIdType userAndPatientId ) {
		return new SupplementalDataStoreAuthProfile( userAndPatientId, userAndPatientId, null ) ;
	}

	public static AuthorizationProfile forOtherPatient( IIdType userId, IIdType patientId ) {
		return new SupplementalDataStoreAuthProfile( userId, patientId, null ) ;
	}

	public static AuthorizationProfile forPractitioner( IIdType practitionerId, LaunchContext launchContext ) {
		return new SupplementalDataStoreAuthProfile( practitionerId, null, launchContext ) ;
	}

	@Override
	public IIdType getAuthorizedUserId() {
		return authorizedUserId;
	}

	@Override
	public IIdType getTargetPatientId() {
		return targetPatientId;
	}

	@Override
	public LaunchContext getLaunchContext() {
		return launchContext;
	}

}