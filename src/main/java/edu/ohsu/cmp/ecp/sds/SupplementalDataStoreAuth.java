package edu.ohsu.cmp.ecp.sds;

import ca.uhn.fhir.rest.api.server.RequestDetails;

import java.net.URI;

import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.instance.model.api.IIdType;

public interface SupplementalDataStoreAuth {

	AuthorizationProfile authorizationProfile(RequestDetails theRequestDetails);

	void addAuthCapability(IBaseConformance theCapabilityStatement, URI authorizeUri, URI tokenUri );

	public interface LaunchContext {
		IIdType getPatient() ;
	}

	public interface AuthorizationProfile {

		IIdType getAuthorizedUserId() ;
		IIdType getTargetPatientId() ;

		LaunchContext getLaunchContext() ;

	}

}
