package edu.ohsu.cmp.ecp.sds;

import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

public abstract class SupplementalDataStoreAuthBase implements SupplementalDataStoreAuth {

	private SupplementalDataStorePermissions permissions; 
	
	public SupplementalDataStoreAuthBase(SupplementalDataStorePermissions permissions) {
		this.permissions = permissions;
	}

	public AuthorizationProfile authorizationProfile(RequestDetails theRequestDetails) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		
		IIdType authorizedUserId = authorizedUserIdFromAuthentication( authentication ) ;
		if ( null == authorizedUserId )
			return null ;

		String authorizedResourceType = authorizedUserId.getResourceType();
		
		if ( "Practitioner".equalsIgnoreCase(authorizedResourceType) )
			return SupplementalDataStoreAuthProfile.forPractitioner( authorizedUserId ) ;

		if ( "Patient".equalsIgnoreCase(authorizedResourceType) )
			return SupplementalDataStoreAuthProfile.forPatient( authorizedUserId ) ;
		
		// authorizedUserId is not a patient; identify the patient for which they have permission
		IIdType writeablePatientId = permissions.resolveWritablePatientIdFor( authorizedUserId, authentication );
		if ( null != writeablePatientId )
			return SupplementalDataStoreAuthProfile.forOtherPatient( authorizedUserId, writeablePatientId ) ;

		throw new AuthenticationException(Msg.code(644) + "Principal \"" + authorizedUserId + "\" Not Authorized For Any Patient");

	}
	
	private IIdType authorizedUserIdFromAuthentication(Authentication authentication) {
		if (null == authentication)
			throw new AuthenticationException(Msg.code(644) + "Missing or Invalid Authorization");

		Object principal = authentication.getPrincipal() ;
		if ( null == principal )
			throw new AuthenticationException(Msg.code(644) + "Missing or Invalid Principal");
		
		if ( !(principal instanceof OAuth2AuthenticatedPrincipal) )
			return null;

		OAuth2AuthenticatedPrincipal oauth2Principal = (OAuth2AuthenticatedPrincipal) authentication.getPrincipal();
		Object subject = oauth2Principal.getAttribute("sub");
		if (null == subject)
			throw new AuthenticationException(Msg.code(644) + "Missing or Invalid Subject");

		return idFromSubject(subject.toString());
	}
	
	protected abstract IIdType idFromSubject( String subject ) ;
}
