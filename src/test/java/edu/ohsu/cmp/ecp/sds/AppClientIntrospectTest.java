package edu.ohsu.cmp.ecp.sds;

import static edu.ohsu.cmp.ecp.sds.SupplementalDataStoreMatchers.identifiesSameResourceAs;
import static edu.ohsu.cmp.ecp.sds.SupplementalDataStorePermissionsInterceptor.getPermissions;
import static java.util.stream.Collectors.joining;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.IdType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RequestDefinition;
import org.mockserver.springtest.MockServerPort;
import org.mockserver.springtest.MockServerTest;
import org.opentest4j.AssertionFailedError;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import edu.ohsu.cmp.ecp.sds.Permissions.ReadAllPatients;
import edu.ohsu.cmp.ecp.sds.Permissions.ReadAndWriteSpecificPatient;
import edu.ohsu.cmp.ecp.sds.Permissions.ReadSpecificPatient;

@MockServerTest
@ActiveProfiles( { "auth-aware-test", "http-aware-test" } )
@TestPropertySource(properties = {
	"spring.security.oauth2.resourceserver.opaque-token.introspection-uri=http://localhost:${mockServerPort}/oauth2/introspect"
})
public class AppClientIntrospectTest extends BaseSuppplementalDataStoreTest {

	private MockServerClient mockServerClient ;

	@MockServerPort
	Integer mockServerPort;

	@MockBean
	SupplementalDataStoreAuthorizationInterceptor supplementalDataStoreAuthorizationInterceptor ;

	private final List<Permissions> authInterceptorPermissions = new ArrayList<>() ; ;

	private Permissions latestPermissions() {
		if ( authInterceptorPermissions.isEmpty() )
			return null ;
		return authInterceptorPermissions.get(0);
	}

	private URL ehrBaseUrl ;
	private String token ;

	@BeforeEach
	public void setupServerUrlAndMetadataResponse() throws MalformedURLException {
		ehrBaseUrl = new URL( "http", "localhost", mockServerPort, "/fhir/R4" );
		mockServerClient.when( metadataRequest() ).respond( metadataResponse() );
	}

	@BeforeEach
	public void setupAuthToken() {
		token = createTestSpecificId() ;
	}

	@BeforeEach
	public void setupAuthCapture() {
		/* ArgumentCaptor is not useful here because the underlying servlet request is cleared before the HTTP request finishes */
		authInterceptorPermissions.clear() ;
		doAnswer( inv -> {
			RequestDetails rq = inv.getArgument(0, RequestDetails.class) ;
			Permissions p = getPermissions( rq ) ;
			authInterceptorPermissions.add( p ) ;
			return null ; /* void response ; no throw for DENY */
		} )
		.when( supplementalDataStoreAuthorizationInterceptor ).incomingRequestPreHandled( any(RequestDetails.class), any(Pointcut.class) ) ;
	}

	private Expectation respondToIntrospectWith( HttpResponse response ) {
		Expectation[] expectations =
			mockServerClient
				.when( oauth2IntrospectRequest() )
				.respond( response )
				;
		return expectations[0] ;
	}

	private Expectation respondToRelatedPersonRequestWith( IIdType relatedPersonId, HttpResponse response ) {
		Expectation[] expectations =
			mockServerClient
				.when( relatedPersonRequest(relatedPersonId) )
				.respond( response )
				;
		return expectations[0] ;
	}

	private void verifyExpectations( Expectation ... expectations) {
		String[] expectationIds = Arrays.stream(expectations).map( Expectation::getId ).toArray( String[]::new ) ;
		mockServerClient.verify( expectationIds ) ;
	}

	private void assertAuthorizationToReadResourceThatisMissing( IIdType resourceId, boolean expectAuthorized ) {
		IGenericClient patientAppClient = authenticatingClient( token ) ;

		if ( expectAuthorized ) {

			assertThrows( ResourceNotFoundException.class, () -> {
				patientAppClient.read().resource( resourceId.getResourceType() ).withId( resourceId.getIdPart() ).execute();
			});

		} else {

			assertThrows( AuthenticationException.class, () -> {
				patientAppClient.read().resource( resourceId.getResourceType() ).withId( resourceId.getIdPart() ).execute();
			});

		}
	}

	private interface Configurator extends Function<IntrospectResponseBuilder, IntrospectResponseBuilder> {} ;

	private static final Configurator WITH_BASE_URL_WITHOUT_FHIR_USER = (builder) -> {
		return builder ;
	};

	private static final Configurator WITH_BASE_URL_WITH_FHIR_USER = (builder) -> {
		return builder.withFhirUser() ;
	};

	private static final Configurator WITHOUT_BASE_URL_WITHOUT_FHIR_USER = (builder) -> {
		return builder.withoutBaseUrl() ;
	};

	private static final Configurator WITHOUT_BASE_URL_WITH_FHIR_USER = (builder) -> {
		return builder.withoutBaseUrl().withFhirUser() ;
	};

	private static final Configurator WITHOUT_RESOURCE_TYPE_WITHOUT_FHIR_USER = (builder) -> {
		return builder.withoutResourceType() ;
	};

	private static final Configurator WITHOUT_RESOURCE_TYPE_WITH_FHIR_USER = (builder) -> {
		return builder.withoutResourceType().withFhirUser() ;
	};

	/************************************************************************************************************
	 * canPermitPatientReadWriteAccessWhenAuthorizationIsPatient (various introspect responses)
	 ************************************************************************************************************/

	private void checkPermissionForPatientReadWriteAccessWhenAuthorizationIsPatient( Configurator configurator, boolean expectAuthorized ) {
		IIdType authorizedPatientId = new IdType( ehrBaseUrl.toString(), "Patient", createTestSpecificId(), null ) ;

		Expectation oauth2Expectation =
			respondToIntrospectWith(
				configurator.apply(
					introspectResponseBuilder().patient(authorizedPatientId)
					)
				.build()
			);

		assertAuthorizationToReadResourceThatisMissing( authorizedPatientId, expectAuthorized ) ;

		verifyExpectations( oauth2Expectation ) ;

		if ( expectAuthorized ) {

			Permissions permissions = latestPermissions();

			assertThat( permissions, notNullValue() ) ;
			ReadAndWriteSpecificPatient readAndWriteSpecificPatient =
				permissions.readAndWriteSpecificPatient()
					.orElseThrow( AssertionFailedError::new );
			assertThat( readAndWriteSpecificPatient.authorizedUserId(), identifiesSameResourceAs( authorizedPatientId ) ) ;
			assertThat( readAndWriteSpecificPatient.patientId().basisUserId(), identifiesSameResourceAs( authorizedPatientId ) ) ;

		}
	}

	@Test
	public void canPermitPatientReadWriteAccessWhenAuthorizationIsPatient_Config001() {
		checkPermissionForPatientReadWriteAccessWhenAuthorizationIsPatient( WITH_BASE_URL_WITHOUT_FHIR_USER, true ) ;
	}

	@Test
	public void canPermitPatientReadWriteAccessWhenAuthorizationIsPatient_Config002() {
		checkPermissionForPatientReadWriteAccessWhenAuthorizationIsPatient( WITH_BASE_URL_WITH_FHIR_USER, true ) ;
	}

	@Test
	public void cannotPermitPatientReadWriteAccessWhenAuthorizationIsPatient_Config003() {
		checkPermissionForPatientReadWriteAccessWhenAuthorizationIsPatient( WITHOUT_BASE_URL_WITHOUT_FHIR_USER, false ) ;
	}

	@Test
	public void canPermitPatientReadWriteAccessWhenAuthorizationIsPatient_Config004() {
		checkPermissionForPatientReadWriteAccessWhenAuthorizationIsPatient( WITHOUT_BASE_URL_WITH_FHIR_USER, true ) ;
	}

	@Test
	public void cannotPermitPatientReadWriteAccessWhenAuthorizationIsPatient_Config005() {
		checkPermissionForPatientReadWriteAccessWhenAuthorizationIsPatient( WITHOUT_RESOURCE_TYPE_WITHOUT_FHIR_USER, false ) ;
	}

	@Test
	public void canPermitPatientReadWriteAccessWhenAuthorizationIsPatient_Config006() {
		checkPermissionForPatientReadWriteAccessWhenAuthorizationIsPatient( WITHOUT_RESOURCE_TYPE_WITH_FHIR_USER, true ) ;
	}


	/************************************************************************************************************
	 * checkPermissionForPatientReadAccessWhenAuthorizationIsProviderInPatientContext (various introspect responses)
	 ************************************************************************************************************/

	private void checkPermissionForPatientReadAccessWhenAuthorizationIsProviderInPatientContext( Configurator configurator, boolean expectPermitted ) {
		IIdType authorizedPractitionerId = new IdType( ehrBaseUrl.toString(), "Practitioner", createTestSpecificId(), null ) ;
		IIdType contextPatientId = new IdType( ehrBaseUrl.toString(), "Patient", createTestSpecificId(), null ) ;

		Expectation oauth2Expectation =
			respondToIntrospectWith(
				configurator.apply(
					introspectResponseBuilder().practitioner(authorizedPractitionerId).context(contextPatientId)
					)
				.build()
			);

		assertAuthorizationToReadResourceThatisMissing( contextPatientId, expectPermitted ) ;

		verifyExpectations( oauth2Expectation ) ;

		if ( expectPermitted ) {

			Permissions permissions = latestPermissions();

			assertThat( permissions, notNullValue() ) ;
			ReadSpecificPatient readSpecificPatient =
				permissions.readSpecificPatient()
					.orElseThrow( AssertionFailedError::new );
			assertThat( readSpecificPatient.authorizedUserId(), identifiesSameResourceAs( authorizedPractitionerId ) ) ;
			assertThat( readSpecificPatient.patientId().basisUserId(), identifiesSameResourceAs( contextPatientId ) ) ;

		}
	}

	@Test
	public void canPermitPatientReadAccessWhenAuthorizationIsProviderInPatientContext_Config001() {
		checkPermissionForPatientReadAccessWhenAuthorizationIsProviderInPatientContext( WITH_BASE_URL_WITHOUT_FHIR_USER, true ) ;
	}

	@Test
	public void canPermitPatientReadAccessWhenAuthorizationIsProviderInPatientContext_Config002() {
		checkPermissionForPatientReadAccessWhenAuthorizationIsProviderInPatientContext( WITH_BASE_URL_WITH_FHIR_USER, true ) ;
	}

	@Test
	public void cannotPermitPatientReadAccessWhenAuthorizationIsProviderInPatientContext_Config003() {
		checkPermissionForPatientReadAccessWhenAuthorizationIsProviderInPatientContext( WITHOUT_BASE_URL_WITHOUT_FHIR_USER, false ) ;
	}

	@Test
	public void canPermitPatientReadAccessWhenAuthorizationIsProviderInPatientContext_Config004() {
		checkPermissionForPatientReadAccessWhenAuthorizationIsProviderInPatientContext( WITHOUT_BASE_URL_WITH_FHIR_USER, true ) ;
	}

	@Test
	public void cannotPermitPatientReadAccessWhenAuthorizationIsProviderInPatientContext_Config005() {
		checkPermissionForPatientReadAccessWhenAuthorizationIsProviderInPatientContext( WITHOUT_RESOURCE_TYPE_WITHOUT_FHIR_USER, false ) ;
	}

	@Test
	public void canPermitPatientReadAccessWhenAuthorizationIsProviderInPatientContext_Config006() {
		checkPermissionForPatientReadAccessWhenAuthorizationIsProviderInPatientContext( WITHOUT_RESOURCE_TYPE_WITH_FHIR_USER, true ) ;
	}

	/************************************************************************************************************
	 * checkPermissionForAllPatientsReadAccessWhenAuthorizationIsProvider (various introspect responses)
	 ************************************************************************************************************/

	private void checkPermissionForAllPatientsReadAccessWhenAuthorizationIsProvider( Configurator configurator, boolean expectAuthorized ) {
		IIdType authorizedPractitionerId = new IdType( ehrBaseUrl.toString(), "Practitioner", createTestSpecificId(), null ) ;

		Expectation oauth2Expectation =
			respondToIntrospectWith(
				configurator.apply(
					introspectResponseBuilder().practitioner(authorizedPractitionerId)
					)
				.build()
			);

		IdType samplePatientId = new IdType( ehrBaseUrl.toString(), "Patient", createTestSpecificId(), null );

		assertAuthorizationToReadResourceThatisMissing( samplePatientId, expectAuthorized ) ;

		verifyExpectations( oauth2Expectation ) ;

		if ( expectAuthorized ) {

			Permissions permissions = latestPermissions();

			assertThat( permissions, notNullValue() ) ;
			ReadAllPatients readAllPatients =
				permissions.readAllPatients()
					.orElseThrow( AssertionFailedError::new );
			assertThat( readAllPatients.authorizedUserId(), identifiesSameResourceAs( authorizedPractitionerId ) ) ;

		}
	}

	@Test
	public void canPermitAllPatientsReadAccessWhenAuthorizationIsProvider_Config001() {
		checkPermissionForAllPatientsReadAccessWhenAuthorizationIsProvider( WITH_BASE_URL_WITHOUT_FHIR_USER, true ) ;
	}

	@Test
	public void canPermitAllPatientsReadAccessWhenAuthorizationIsProvider_Config002() {
		checkPermissionForAllPatientsReadAccessWhenAuthorizationIsProvider( WITH_BASE_URL_WITH_FHIR_USER, true ) ;
	}

	@Test
	public void cannotPermitAllPatientsReadAccessWhenAuthorizationIsProvider_Config003() {
		checkPermissionForAllPatientsReadAccessWhenAuthorizationIsProvider( WITHOUT_BASE_URL_WITHOUT_FHIR_USER, false ) ;
	}

	@Test
	public void canPermitAllPatientsReadAccessWhenAuthorizationIsProvider_Config004() {
		checkPermissionForAllPatientsReadAccessWhenAuthorizationIsProvider( WITHOUT_BASE_URL_WITH_FHIR_USER, true ) ;
	}

	@Test
	public void cannotPermitAllPatientsReadAccessWhenAuthorizationIsProvider_Config005() {
		checkPermissionForAllPatientsReadAccessWhenAuthorizationIsProvider( WITHOUT_RESOURCE_TYPE_WITHOUT_FHIR_USER, false ) ;
	}

	@Test
	public void canPermitAllPatientsReadAccessWhenAuthorizationIsProvider_Config006() {
		checkPermissionForAllPatientsReadAccessWhenAuthorizationIsProvider( WITHOUT_RESOURCE_TYPE_WITH_FHIR_USER, true ) ;
	}

	/************************************************************************************************************
	 * checkPermissionForPatientReadWriteAccessWhenAuthorizationIsRelatedPerson (various introspect responses)
	 ************************************************************************************************************/

	private void checkPermissionForPatientReadWriteAccessWhenAuthorizationIsRelatedPerson( Configurator configurator, boolean expectAuthorized ) {
		IIdType authorizedRelatedPersonId = new IdType( ehrBaseUrl.toString(), "RelatedPerson", createTestSpecificId(), null ) ;
		IIdType authorizedPatientId = new IdType( ehrBaseUrl.toString(), "Patient", createTestSpecificId(), null ) ;

		Expectation oauth2Expectation =
			respondToIntrospectWith(
				configurator.apply(
					introspectResponseBuilder().relatedPerson(authorizedRelatedPersonId)
					)
				.build()
			);

		Expectation relatedPersonExpectation =
			respondToRelatedPersonRequestWith(
				authorizedRelatedPersonId,
				relatedPersonResponse(authorizedRelatedPersonId, authorizedPatientId)
			);

		assertAuthorizationToReadResourceThatisMissing( authorizedPatientId, expectAuthorized ) ;

		verifyExpectations( oauth2Expectation ) ;

		if ( expectAuthorized ) {

			verifyExpectations( relatedPersonExpectation ) ;

			Permissions permissions = latestPermissions();

			assertThat( permissions, notNullValue() ) ;
			ReadAndWriteSpecificPatient readAndWriteSpecificPatient =
				permissions.readAndWriteSpecificPatient()
					.orElseThrow( AssertionFailedError::new );
			assertThat( readAndWriteSpecificPatient.authorizedUserId(), identifiesSameResourceAs( authorizedRelatedPersonId ) ) ;
			assertThat( readAndWriteSpecificPatient.patientId().basisUserId(), identifiesSameResourceAs( authorizedPatientId ) ) ;
		}
	}

	@Test
	public void canPermitPatientReadWriteAccessWhenAuthorizationIsRelatedPerson_Config001() {
		checkPermissionForPatientReadWriteAccessWhenAuthorizationIsRelatedPerson( WITH_BASE_URL_WITHOUT_FHIR_USER, true ) ;
	}

	@Test
	public void canPermitPatientReadWriteAccessWhenAuthorizationIsRelatedPerson_Config002() {
		checkPermissionForPatientReadWriteAccessWhenAuthorizationIsRelatedPerson( WITH_BASE_URL_WITH_FHIR_USER, true ) ;
	}

	@Test
	public void cannotPermitPatientReadWriteAccessWhenAuthorizationIsRelatedPerson_Config003() {
		checkPermissionForPatientReadWriteAccessWhenAuthorizationIsRelatedPerson( WITHOUT_BASE_URL_WITHOUT_FHIR_USER, false ) ;
	}

	@Test
	public void canPermitPatientReadWriteAccessWhenAuthorizationIsRelatedPerson_Config004() {
		checkPermissionForPatientReadWriteAccessWhenAuthorizationIsRelatedPerson( WITHOUT_BASE_URL_WITH_FHIR_USER, true ) ;
	}

	@Test
	public void cannotPermitPatientReadWriteAccessWhenAuthorizationIsRelatedPerson_Config005() {
		checkPermissionForPatientReadWriteAccessWhenAuthorizationIsRelatedPerson( WITHOUT_RESOURCE_TYPE_WITHOUT_FHIR_USER, false ) ;
	}

	@Test
	public void canPermitPatientReadWriteAccessWhenAuthorizationIsRelatedPerson_Config006() {
		checkPermissionForPatientReadWriteAccessWhenAuthorizationIsRelatedPerson( WITHOUT_RESOURCE_TYPE_WITH_FHIR_USER, true ) ;
	}

	/************************************************************************************************************
	 * request and response helpers
	 ************************************************************************************************************/

	private HttpRequest metadataRequest( ) {
		return request()
			.withMethod("GET")
			.withPath( ehrBaseUrl.getPath() + "/metadata")
			;
	}

	private HttpResponse metadataResponse() {
		String jsonBody =
			"{ \"resourceType\": \"CapabilityStatement\" }"
			;
		return response()
			.withStatusCode( 200 )
			.withBody( json( jsonBody ) )
			;

	}

	private RequestDefinition oauth2IntrospectRequest() {
		return request()
			.withMethod( "POST" )
			.withPath( "/oauth2/introspect" )
			.withHeader( "Authorization", "Bearer " + token )
			.withBody( "token=" + token )
			;
	}

	private interface IntrospectResponseBuilder {
		IntrospectResponseBuilder patient( IIdType userId ) ;
		IntrospectResponseBuilder practitioner( IIdType userId ) ;
		IntrospectResponseBuilder context( IIdType context ) ;
		IntrospectResponseBuilder relatedPerson( IIdType userId ) ;
		IntrospectResponseBuilder withoutBaseUrl() ;
		IntrospectResponseBuilder withoutResourceType() ;
		IntrospectResponseBuilder withFhirUser() ;
		HttpResponse build() ;
	}

	IntrospectResponseBuilder introspectResponseBuilder() {
		return new IntrospectResponseBuilder() {
			private IIdType subject ;
			private IIdType context ;
			private boolean includeBaseUrlInSubject = true ;
			private boolean includeResourceTypeInSubject = true;
			private boolean includeFhirUserFromSubject = false ;

			public IntrospectResponseBuilder patient( IIdType patientId ) {
				if ( !"Patient".equals(patientId.getResourceType()) )
					throw new AssertionFailedError("IntrospectResponseBuilder(...) expected a user of type \"Patient\" but received \"" + patientId.getResourceType() + "\"" ) ;
				this.subject = patientId ;
				return this ;
			}

			public IntrospectResponseBuilder practitioner( IIdType practitionerId ) {
				if ( !"Practitioner".equals(practitionerId.getResourceType()) )
					throw new AssertionFailedError("IntrospectResponseBuilder(...) expected a user of type \"Practitioner\" but received \"" + practitionerId.getResourceType() + "\"" ) ;
				this.subject = practitionerId ;
				return this ;
			}

			public IntrospectResponseBuilder context( IIdType context ) {
				if ( !"Patient".equals(context.getResourceType()) )
					throw new AssertionFailedError("IntrospectResponseBuilder(...) expected a user of type \"Patient\" but received \"" + context.getResourceType() + "\"" ) ;
				this.context = context ;
				return this ;
			}

			public IntrospectResponseBuilder relatedPerson( IIdType relatedPersonId ) {
				if ( !"RelatedPerson".equals(relatedPersonId.getResourceType()) )
					throw new AssertionFailedError("IntrospectResponseBuilder(...) expected a user of type \"RelatedPerson\" but received \"" + relatedPersonId.getResourceType() + "\"" ) ;
				this.subject = relatedPersonId ;
				return this ;
			}

			public IntrospectResponseBuilder withoutBaseUrl() {
				this.includeBaseUrlInSubject = false ;
				return this ;
			}

			public IntrospectResponseBuilder withoutResourceType() {
				this.includeBaseUrlInSubject = false ;
				this.includeResourceTypeInSubject = false ;
				return this ;
			}

			public IntrospectResponseBuilder withFhirUser() {
				this.includeFhirUserFromSubject = true ;
				return this ;
			}

			private String initialToLowerCase( String s ) {
				if ( s.isEmpty() )
					return s ;
				String initial = s.substring(0, 1);
				if ( s.length() < 2 )
					return initial ;
				String remaining = s.substring( 1 );
				return initial.toLowerCase() + remaining ;
			}

			public HttpResponse build() {
				if ( null == subject )
					throw new AssertionFailedError("IntrospectResponseBuilder(...) expected a subject" ) ;

				if ( null != context && !"Practitioner".equals(subject.getResourceType()) )
					throw new AssertionFailedError("IntrospectResponseBuilder(...) expected a subject of type \"Practitioner\" when a context is present but received \"" + subject.getResourceType() + "\"" ) ;

				Map<String,Object> fields = new HashMap<>() ;

				fields.put( "active", true ) ;
				fields.put( "exp", Long.toString( System.currentTimeMillis() + 60000 ) );

				if ( includeFhirUserFromSubject )
					fields.put( "fhirUser", "\"" + subject + "\"" ) ;

				if ( includeBaseUrlInSubject )
					fields.put( "sub", "\"" + subject + "\"" ) ;
				else if ( includeResourceTypeInSubject )
					fields.put( "sub", "\"" + subject.toUnqualifiedVersionless() + "\"" ) ;
				else
					fields.put( "sub", "\"" + subject.getIdPart() + "\"" ) ;

				if ( null != context )
					fields.put( initialToLowerCase(context.getResourceType()), "\"" + context + "\"" ) ;

				String jsonBody = fields.entrySet().stream().map( e -> "\"" + e.getKey() + "\": " + e.getValue() ).collect( joining(", ", "{ ", " }") ) ;
				return response()
					.withStatusCode( 200 )
					.withBody( json( jsonBody ) )
					;
			}
		} ;
	}

	private RequestDefinition relatedPersonRequest( IIdType relatedPersonId ) {
		if ( !"RelatedPerson".equals( relatedPersonId.getResourceType() ) )
			throw new AssertionFailedError("relatedPersonRequest(...) expected a related person of type \"RelatedPerson\" but encountered \"" + relatedPersonId.getResourceType() + "\"" ) ;
		return request()
			.withMethod( "GET" )
			.withPath( ehrBaseUrl.getPath()+ "/" + relatedPersonId.getResourceType() + "/" + relatedPersonId.getIdPart() )
			.withHeader( "Authorization", "Bearer " + token )
			;
	}

	private HttpResponse relatedPersonResponse( IIdType relatedPersonId, IIdType patientId ) {
		if ( !"RelatedPerson".equals( relatedPersonId.getResourceType() ) )
			throw new AssertionFailedError("relatedPersonResponse(...) expected a related person of type \"RelatedPerson\" but encountered \"" + relatedPersonId.getResourceType() + "\"" ) ;
		if ( !"Patient".equals( patientId.getResourceType() ) )
			throw new AssertionFailedError("relatedPersonResponse(...) expected a patient of type \"Patient\" but encountered \"" + patientId.getResourceType() + "\"" ) ;
		String jsonBody =
			String.format(
				"{ \"resourceType\": \"RelatedPerson\", \"id\": \"%2$s\", \"patient\": { \"reference\": \"%1$s\" } }",
				patientId,
				relatedPersonId
				);
		return response()
			.withStatusCode( 200 )
			.withBody( json( jsonBody ) )
			;

	}

}
