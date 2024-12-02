package edu.ohsu.cmp.ecp.sds;

import java.util.function.Consumer;

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
			return SupplementalDataStoreAuthProfile.forPractitioner( authorizedUserId, launchContextFromAuthentication(authentication) ) ;

		if ( "Patient".equalsIgnoreCase(authorizedResourceType) )
			return SupplementalDataStoreAuthProfile.forPatient( authorizedUserId ) ;
		
		// authorizedUserId is not a patient; identify the patient for which they have permission
		IIdType writeablePatientId = permissions.resolveWritablePatientIdFor( authorizedUserId, authentication );
		if ( null != writeablePatientId )
			return SupplementalDataStoreAuthProfile.forOtherPatient( authorizedUserId, writeablePatientId ) ;

		throw new AuthenticationException(Msg.code(644) + "Principal \"" + authorizedUserId + "\" Not Authorized For Any Patient");

	}
	private OAuth2AuthenticatedPrincipal oauth2PrincipalFromAuthentication(Authentication authentication) {
		if (null == authentication)
			throw new AuthenticationException(Msg.code(644) + "Missing or Invalid Authorization");

		Object principal = authentication.getPrincipal() ;
		if ( null == principal )
			throw new AuthenticationException(Msg.code(644) + "Missing or Invalid Principal");
		
		if ( !(principal instanceof OAuth2AuthenticatedPrincipal) )
			return null;

		OAuth2AuthenticatedPrincipal oauth2Principal = (OAuth2AuthenticatedPrincipal) authentication.getPrincipal();
		return oauth2Principal ;
	}

	private IIdType authorizedUserIdFromAuthentication(Authentication authentication) {
		OAuth2AuthenticatedPrincipal oauth2Principal = oauth2PrincipalFromAuthentication(authentication);
		return authorizedUserIdFromOAuth2Principal( oauth2Principal );
	}

	private IIdType authorizedUserIdFromOAuth2Principal( OAuth2AuthenticatedPrincipal oauth2Principal ) {
		if ( null == oauth2Principal )
			return null ;

		Object subject = oauth2Principal.getAttribute("sub");
		if (null == subject)
			throw new AuthenticationException(Msg.code(644) + "Missing or Invalid Subject");

		IIdType subjectId = idFromSubject(subject.toString());

		if ( subjectId.hasBaseUrl() && subjectId.hasResourceType() )
			return subjectId;

		Object fhirUser = oauth2Principal.getAttribute("fhirUser");
		if (null == fhirUser)
			throw new AuthenticationException(Msg.code(644) + "Incomplete Subject and Missing FhirUser");

		IIdType fhirUserId = idFromSubject(fhirUser.toString());

		if ( !fhirUserId.hasIdPart() )
			throw new AuthenticationException(Msg.code(644) + "Incomplete Subject and Invalid FhirUser");

		if ( !fhirUserId.getIdPart().equals( subjectId.getIdPart() ) )
			throw new AuthenticationException(Msg.code(644) + "Incomplete Subject and Mismatch Between Subject And FhirUser");

		return fhirUserId ;
	}

	private LaunchContext launchContextFromAuthentication(Authentication authentication) {
		OAuth2AuthenticatedPrincipal oauth2Principal = oauth2PrincipalFromAuthentication(authentication);
		if ( null == oauth2Principal )
			return null ;

		Object contextPatient = oauth2Principal.getAttribute("patient");
		if (null == contextPatient)
			return null;

		IIdType contextPatientId =
			coerceToResourceType(
				idFromContextParameter( contextPatient.toString() ),
				"Patient",
				(actualResourceType) -> {
					throw new AuthenticationException(Msg.code(644) + "Launch Context Patient \"" + contextPatient + "\" must be the id of a patient, but found a resource type of \"" + actualResourceType + "\"");
				}
			) ;
		IIdType fullyQualifiedContextPatientId = fullyQualifiedContextPatientId( contextPatientId, oauth2Principal );

		return new LaunchContext() {

			@Override
			public IIdType getPatient() {
				return fullyQualifiedContextPatientId;
			}

		};
	}

	private static IIdType coerceToResourceType( IIdType id, String resourceType, Consumer<String> onResourceTypeMismatch ) {
		if ( id.hasResourceType() ) {
			if ( null != onResourceTypeMismatch && !resourceType.equals( id.getResourceType() ) ) {
				onResourceTypeMismatch.accept( id.getResourceType() ) ;
			}
		}

		if ( id.hasBaseUrl() ) {
			return id.withServerBase( id.getBaseUrl(), resourceType ) ;
		} else {
			return id.withResourceType( resourceType ) ;
		}
	}

	private IIdType fullyQualifiedContextPatientId( IIdType contextPatientId, OAuth2AuthenticatedPrincipal oauth2Principal ) {
		if ( contextPatientId.hasBaseUrl() )
			return contextPatientId ;

		IIdType authorizedUserId = authorizedUserIdFromOAuth2Principal( oauth2Principal );
		if ( null == authorizedUserId )
			throw new AuthenticationException(Msg.code(644) + "Launch Context Patient \"" + contextPatientId + "\" is missing required base url, but no authorized user id is available to provide one");
		if ( !authorizedUserId.hasBaseUrl() )
			throw new AuthenticationException(Msg.code(644) + "Launch Context Patient \"" + contextPatientId + "\" is missing required base url, but authorized user id \"" + authorizedUserId + "\" does not provide one");

		return contextPatientId.withServerBase( authorizedUserId.getBaseUrl(), contextPatientId.getResourceType() ) ;
	}

	protected abstract IIdType idFromSubject( String subject ) ;

	protected abstract IIdType idFromContextParameter( String contextParameterValue ) ;

}
