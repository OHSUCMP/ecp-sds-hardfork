package edu.ohsu.cmp.ecp.sds;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import java.util.List;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Linkage;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RequestDefinition;
import org.mockserver.springtest.MockServerPort;
import org.mockserver.springtest.MockServerTest;
import org.opentest4j.AssertionFailedError;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import edu.ohsu.cmp.ecp.sds.assertions.PartitionAssertions;
import edu.ohsu.cmp.ecp.sds.assertions.SdsAssertions;

@MockServerTest
@ActiveProfiles( { "auth-aware-test", "http-aware-test" } )
@TestPropertySource(properties = {
	"spring.security.oauth2.resourceserver.opaque-token.introspection-uri=http://localhost:${mockServerPort}/oauth2/introspect"
})
public class PractitionerAppClientTest extends BaseSuppplementalDataStoreTest {

	private static final String FOREIGN_PARTITION_A_NAME = "http://my.ehr.org/fhir/R4/" ;
	private static final String FOREIGN_PARTITION_OTHER_NAME = "http://community.ehr.net/" ;

	IIdType aardvarkPatientId ;
	IIdType basiliskPatientId ;
	IIdType crocodilePatientId ;

	IIdType applePatientId ;
	IIdType bananaPatientId ;
	IIdType coconutPatientId ;

	private IIdType authorizedPractitionerId;

	private SdsAssertions sdsAssertions ;
	private PartitionAssertions localPartition ;
	private PartitionAssertions aardvarkPartition ;
	private PartitionAssertions basiliskPartition ;
	private PartitionAssertions crocodilePartition ;
	private PartitionAssertions applePartition ;
	private PartitionAssertions bananaPartition ;
	private PartitionAssertions coconutPartition ;

	private SdsAssertions sdsAssertionsForAardvarkContext ;
	private PartitionAssertions localPartitionForAardvarkContext ;
	private PartitionAssertions aardvarkPartitionForAardvarkContext ;
	private PartitionAssertions applePartitionForAardvarkContext ;

	private MockServerClient mockServerClient ;

	@MockServerPort
	Integer mockServerPort;

	private String createTokenThatAuthorizesUser( IIdType userId ) {
		String token = createTestSpecificId();
		mockServerClient
			.when( oauth2IntrospectRequest(token) )
			.respond( oauth2IntrospectResponse( userId ) )
			;
		return token ;
	}

	private String createTokenAuthorizingUserInPatientContext( IIdType userId, IIdType contextPatientId ) {
		String token = createTestSpecificId();
		mockServerClient
			.when( oauth2IntrospectRequest(token) )
			.respond( oauth2IntrospectResponse( userId, contextPatientId ) )
			;
		return token ;
	}

	@BeforeEach
	public void setupMetadata() {
		mockServerClient
			.when( metadataRequest() )
			.respond( metadataResponse() )
			;
	}

	@BeforeEach
	public void setupPractionerAccess() {
		aardvarkPatientId = new IdType( FOREIGN_PARTITION_A_NAME, "Patient", createTestSpecificId(), null ) ;
		basiliskPatientId = new IdType( FOREIGN_PARTITION_A_NAME, "Patient", createTestSpecificId(), null ) ;
		crocodilePatientId = new IdType( FOREIGN_PARTITION_A_NAME, "Patient", createTestSpecificId(), null ) ;

		applePatientId = new IdType( FOREIGN_PARTITION_OTHER_NAME, "Patient", createTestSpecificId(), null ) ;
		bananaPatientId = new IdType( FOREIGN_PARTITION_OTHER_NAME, "Patient", createTestSpecificId(), null ) ;
		coconutPatientId = new IdType( FOREIGN_PARTITION_OTHER_NAME, "Patient", createTestSpecificId(), null ) ;


		authorizedPractitionerId = new IdType( FOREIGN_PARTITION_A_NAME, "Practitioner", createTestSpecificId(), null ) ;
		String token = createTokenThatAuthorizesUser( authorizedPractitionerId ) ;
		String tokenForAardvarkContext = createTokenAuthorizingUserInPatientContext( authorizedPractitionerId, aardvarkPatientId ) ;

		IGenericClient localClient = authenticatingClient( token ) ;
		IGenericClient foreignClient = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_A_NAME ) ;
		IGenericClient otherForeignClient = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_OTHER_NAME ) ;

		IGenericClient localClientForAardvarkContext = authenticatingClient( tokenForAardvarkContext ) ;
		IGenericClient foreignClientForAardvarkContext = authenticatingClientTargetingPartition( tokenForAardvarkContext, FOREIGN_PARTITION_A_NAME ) ;
		IGenericClient otherForeignClientForAardvarkContext = authenticatingClientTargetingPartition( tokenForAardvarkContext, FOREIGN_PARTITION_OTHER_NAME ) ;

		sdsAssertions = new SdsAssertions( localClient ) ;

		localPartition = sdsAssertions.local() ;
		aardvarkPartition = sdsAssertions.foreign( foreignClient, aardvarkPatientId ) ;
		basiliskPartition = sdsAssertions.foreign( foreignClient, basiliskPatientId ) ;
		crocodilePartition = sdsAssertions.foreign( foreignClient, crocodilePatientId ) ;

		applePartition = sdsAssertions.foreign( otherForeignClient, applePatientId ) ;
		bananaPartition = sdsAssertions.foreign( otherForeignClient, bananaPatientId ) ;
		coconutPartition = sdsAssertions.foreign( otherForeignClient, coconutPatientId ) ;

		sdsAssertionsForAardvarkContext = new SdsAssertions( localClientForAardvarkContext ) ;

		localPartitionForAardvarkContext = sdsAssertionsForAardvarkContext.local() ;
		aardvarkPartitionForAardvarkContext = sdsAssertionsForAardvarkContext.foreign( foreignClientForAardvarkContext, aardvarkPatientId ) ;

		applePartitionForAardvarkContext = sdsAssertionsForAardvarkContext.foreign( otherForeignClientForAardvarkContext, applePatientId ) ;
	}

	private IIdType storeNewQuestionnaireResponseForPatient( IIdType userId, IIdType patientId ) {
		String token = createTokenThatAuthorizesUser( userId );

		QuestionnaireResponse questionnaireResponse  = new QuestionnaireResponse() ;
		questionnaireResponse.setSubject( new Reference( patientId.toUnqualifiedVersionless() ) ) ;
		questionnaireResponse.setQuestionnaire( createTestSpecificId() ) ;

		if ( patientId.hasBaseUrl() && !patientId.getBaseUrl().equals( fhirServerlBase() )) {
			IGenericClient patientAppClient = authenticatingClientTargetingPartition( token, patientId.getBaseUrl() ) ;

			/* NOTE
			 * later searches will fail to find this QuestionnaireResource unless
			 * the related Patient resource for the compartment is created first
			 * ---
			 * this should not be required, since the SDS is expected to create stub resources
			 * when claiming the partition
			 */
			try {
				patientAppClient.read().resource(Patient.class).withId( patientId.toUnqualifiedVersionless() ).execute() ;
			} catch ( ResourceNotFoundException | ForbiddenOperationException ex ) {
				Patient p = new Patient() ;
				p.setId( patientId.getIdPart() );
				patientAppClient.update().resource(p).execute() ;
			}

			questionnaireResponse.setIdElement( new IdType( "QuestionnaireResponse", createTestSpecificId() ) ) ;
			IIdType questRespId = patientAppClient.update().resource(questionnaireResponse).execute().getId();
			return questRespId ;
		} else {
			IGenericClient patientAppClient = authenticatingClient( token ) ;
			IIdType questRespId = patientAppClient.create().resource(questionnaireResponse).execute().getId();
			return questRespId ;
		}
	}

	@Test
	void cannotReadMultipleDistinctPatientsWithPatientAuthorization() {
		SdsAssertions sdsPatientAssertions = new SdsAssertions( authenticatingClient( createTokenThatAuthorizesUser( aardvarkPatientId ) ) ) ;
		PartitionAssertions aardvarkPatientPartition = sdsPatientAssertions.foreign( authenticatingClientTargetingPartition( createTokenThatAuthorizesUser( aardvarkPatientId ), FOREIGN_PARTITION_A_NAME ), aardvarkPatientId ) ;
		PartitionAssertions localPatientPartition = sdsPatientAssertions.local() ;
		PartitionAssertions applePatientPartition = sdsPatientAssertions.foreign( authenticatingClientTargetingPartition( createTokenThatAuthorizesUser( aardvarkPatientId ), FOREIGN_PARTITION_OTHER_NAME ), applePatientId ) ;

		aardvarkPatientPartition.assertUnclaimed();

		IIdType questRespAardvarkId = storeNewQuestionnaireResponseForPatient( aardvarkPatientId, aardvarkPatientId ) ;
		IIdType questRespBasiliskId = storeNewQuestionnaireResponseForPatient( basiliskPatientId, basiliskPatientId ) ;
		IIdType questRespCrocodileId = storeNewQuestionnaireResponseForPatient( crocodilePatientId, crocodilePatientId ) ;
		IIdType questRespAppleId = storeNewQuestionnaireResponseForPatient( aardvarkPatientId, applePatientId ) ;
		IIdType questRespBananaId = storeNewQuestionnaireResponseForPatient( basiliskPatientId, bananaPatientId ) ;
		IIdType questRespCoconutId = storeNewQuestionnaireResponseForPatient( crocodilePatientId, coconutPatientId ) ;

		IIdType localPatientId = sdsPatientAssertions.linkages().assertUniqueSourceItemPresent( aardvarkPatientId ) ;
		localPatientPartition.id().update( localPatientId ) ;

		List<Linkage> linkages = sdsPatientAssertions.linkages().assertPresent( localPatientId ) ;
		assertThat( linkages, hasSize(2) ) ;

		aardvarkPatientPartition.assertClaimed();
		localPatientPartition.assertClaimed() ;
		applePatientPartition.assertClaimed();

		List<QuestionnaireResponse> aardvarkAllQuestResps = aardvarkPatientPartition.operations().resources( QuestionnaireResponse.class ).search( QuestionnaireResponse.SUBJECT ) ;
		assertThat( aardvarkAllQuestResps, hasSize(1) );
		assertThat( aardvarkPatientPartition.operations().resources( QuestionnaireResponse.class ).read( questRespAardvarkId.toUnqualifiedVersionless() ), notNullValue() );
		assertThat( applePatientPartition.operations().resources( QuestionnaireResponse.class ).read( questRespAppleId.toUnqualifiedVersionless() ), notNullValue() );

		assertThrows( ResourceNotFoundException.class, () -> {
			aardvarkPatientPartition.operations().resources( QuestionnaireResponse.class ).read( questRespBasiliskId.toUnqualifiedVersionless() );
		}) ;
		assertThrows( ResourceNotFoundException.class, () -> {
			applePatientPartition.operations().resources( QuestionnaireResponse.class ).read( questRespBananaId.toUnqualifiedVersionless() );
		}) ;

		assertThrows( ResourceNotFoundException.class, () -> {
			aardvarkPatientPartition.operations().resources( QuestionnaireResponse.class ).read( questRespCrocodileId.toUnqualifiedVersionless() );
		}) ;
		assertThrows( ResourceNotFoundException.class, () -> {
			applePatientPartition.operations().resources( QuestionnaireResponse.class ).read( questRespCoconutId.toUnqualifiedVersionless() );
		}) ;
	}

	@Test
	void canReadMultipleDistinctPatientsWithPractitionerAuthorization() {
		aardvarkPartition.assertUnclaimed();

		IIdType questRespAardvarkId = storeNewQuestionnaireResponseForPatient( aardvarkPatientId, aardvarkPatientId ) ;
		IIdType questRespBasiliskId = storeNewQuestionnaireResponseForPatient( basiliskPatientId, basiliskPatientId ) ;
		IIdType questRespCrocodileId = storeNewQuestionnaireResponseForPatient( crocodilePatientId, crocodilePatientId ) ;
		IIdType questRespAppleId = storeNewQuestionnaireResponseForPatient( aardvarkPatientId, applePatientId ) ;
		IIdType questRespBananaId = storeNewQuestionnaireResponseForPatient( basiliskPatientId, bananaPatientId ) ;
		IIdType questRespCoconutId = storeNewQuestionnaireResponseForPatient( crocodilePatientId, coconutPatientId ) ;

		IIdType localPatientId = sdsAssertions.linkages().assertUniqueSourceItemPresent( aardvarkPatientId ) ;
		localPartition.id().update( localPatientId ) ;

		List<Linkage> linkages = sdsAssertions.linkages().assertPresent( localPatientId ) ;
		assertThat( linkages, hasSize(2) ) ;

		aardvarkPartition.assertClaimed();
		localPartition.assertClaimed() ;
		applePartition.assertClaimed();

		assertThat( aardvarkPartition.operations().resources( QuestionnaireResponse.class ).search( QuestionnaireResponse.SUBJECT ), hasSize(1) );
		assertThat( applePartition.operations().resources( QuestionnaireResponse.class ).search( QuestionnaireResponse.SUBJECT ), hasSize(1) );

		assertThat( basiliskPartition.operations().resources( QuestionnaireResponse.class ).search( QuestionnaireResponse.SUBJECT ), hasSize(1) );
		assertThat( bananaPartition.operations().resources( QuestionnaireResponse.class ).search( QuestionnaireResponse.SUBJECT ), hasSize(1) );

		assertThat( crocodilePartition.operations().resources( QuestionnaireResponse.class ).search( QuestionnaireResponse.SUBJECT ), hasSize(1) );
		assertThat( coconutPartition.operations().resources( QuestionnaireResponse.class ).search( QuestionnaireResponse.SUBJECT ), hasSize(1) );

		assertThat( aardvarkPartition.operations().resources( QuestionnaireResponse.class ).read( questRespAardvarkId ), notNullValue() );
		assertThat( applePartition.operations().resources( QuestionnaireResponse.class ).read( questRespAppleId ), notNullValue() );

		assertThat( aardvarkPartition.operations().resources( QuestionnaireResponse.class ).read( questRespBasiliskId ), notNullValue() );
		assertThat( applePartition.operations().resources( QuestionnaireResponse.class ).read( questRespBananaId ), notNullValue() );

		assertThat( aardvarkPartition.operations().resources( QuestionnaireResponse.class ).read( questRespCrocodileId ), notNullValue() );
		assertThat( applePartition.operations().resources( QuestionnaireResponse.class ).read( questRespCoconutId ), notNullValue() );
	}

	@Test
	void cannotReadMultipleDistinctPatientsWithPractitionerAuthorizationInPatientContext() {
		aardvarkPartitionForAardvarkContext.assertUnclaimed();

		IIdType questRespAardvarkId = storeNewQuestionnaireResponseForPatient( aardvarkPatientId, aardvarkPatientId ) ;
		IIdType questRespBasiliskId = storeNewQuestionnaireResponseForPatient( basiliskPatientId, basiliskPatientId ) ;
		IIdType questRespCrocodileId = storeNewQuestionnaireResponseForPatient( crocodilePatientId, crocodilePatientId ) ;
		IIdType questRespAppleId = storeNewQuestionnaireResponseForPatient( aardvarkPatientId, applePatientId ) ;
		IIdType questRespBananaId = storeNewQuestionnaireResponseForPatient( basiliskPatientId, bananaPatientId ) ;
		IIdType questRespCoconutId = storeNewQuestionnaireResponseForPatient( crocodilePatientId, coconutPatientId ) ;

		IIdType localPatientId = sdsAssertionsForAardvarkContext.linkages().assertUniqueSourceItemPresent( aardvarkPatientId ) ;
		localPartitionForAardvarkContext.id().update( localPatientId ) ;

		List<Linkage> linkages = sdsAssertionsForAardvarkContext.linkages().assertPresent( localPatientId ) ;
		assertThat( linkages, hasSize(2) ) ;

		aardvarkPartitionForAardvarkContext.assertClaimed();
		localPartitionForAardvarkContext.assertClaimed() ;
		applePartitionForAardvarkContext.assertClaimed();

		assertThat( aardvarkPartitionForAardvarkContext.operations().resources( QuestionnaireResponse.class ).read( questRespAardvarkId ), notNullValue() );
		assertThat( applePartitionForAardvarkContext.operations().resources( QuestionnaireResponse.class ).read( questRespAppleId ), notNullValue() );

		assertThrows( ResourceNotFoundException.class, () -> {
			aardvarkPartitionForAardvarkContext.operations().resources( QuestionnaireResponse.class ).read( questRespBasiliskId );
		}) ;
		assertThrows( ResourceNotFoundException.class, () -> {
			applePartitionForAardvarkContext.operations().resources( QuestionnaireResponse.class ).read( questRespBananaId );
		}) ;

		assertThrows( ResourceNotFoundException.class, () -> {
			aardvarkPartitionForAardvarkContext.operations().resources( QuestionnaireResponse.class ).read( questRespCrocodileId );
		}) ;
		assertThrows( ResourceNotFoundException.class, () -> {
			applePartitionForAardvarkContext.operations().resources( QuestionnaireResponse.class ).read( questRespCoconutId );
		}) ;
	}

	private RequestDefinition oauth2IntrospectRequest( String token ) {
		return request()
			.withMethod( "POST" )
			.withPath( "/oauth2/introspect" )
			.withHeader( "Authorization", "Bearer " + token )
			.withBody( "token=" + token )
			;
	}

	private HttpResponse oauth2IntrospectResponse( IIdType user ) {
		String jsonBody =
			String.format(
				"{ \"active\": true, \"sub\": \"%1$s\", \"exp\": %2$d }", // NOTE: no key for "patient" context
				user.toString(),
				System.currentTimeMillis() + 60000
				);
		return response()
			.withStatusCode( 200 )
			.withBody( json( jsonBody ) )
			;

	}

	private HttpResponse oauth2IntrospectResponse( IIdType practitioner, IIdType patientContext ) {
		if ( !"Practitioner".equals( practitioner.getResourceType() ) )
			throw new AssertionFailedError("oauth2IntrospectResponse(...) included a patientContext, so expected a user of type \"Practitioner\" but encountered \"" + practitioner.getResourceType() + "\"" ) ;
		if ( !"Patient".equals( patientContext.getResourceType() ) )
			throw new AssertionFailedError("oauth2IntrospectResponse(...) included a patientContext, but its type was \"" + patientContext.getResourceType() + "\"" ) ;
		if ( patientContext.hasBaseUrl() && practitioner.hasBaseUrl() ) {
			if ( !patientContext.getBaseUrl().equals( practitioner.getBaseUrl() ) ) {
				throw new AssertionFailedError("oauth2IntrospectResponse(...) included a patientContext with base url \"" + patientContext.getBaseUrl() + "\" different from practitioner base url \"" + practitioner.getBaseUrl() + "\"" ) ;
			}
		}
		String jsonBody =
			String.format(
				"{ \"active\": true, \"sub\": \"%1$s\", \"exp\": %2$d, \"patient\": \"%3$s\" }",
				practitioner.toString(),
				System.currentTimeMillis() + 60000,
				patientContext.getIdPart()
				);
		return response()
				.withStatusCode( 200 )
				.withBody( json( jsonBody ) )
				;

	}

	private HttpRequest metadataRequest() {
		return request()
			.withMethod("GET")
			.withPath("/fhir/metadata")
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
}
