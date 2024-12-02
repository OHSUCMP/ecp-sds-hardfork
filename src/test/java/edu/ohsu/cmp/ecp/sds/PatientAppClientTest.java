package edu.ohsu.cmp.ecp.sds;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static edu.ohsu.cmp.ecp.sds.SupplementalDataStoreMatchers.identifiesResource;
import static edu.ohsu.cmp.ecp.sds.SupplementalDataStoreMatchers.identifiesSameResourceAs;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestSecurityComponent;
import org.hl7.fhir.r4.model.Linkage.LinkageItemComponent;
import org.hl7.fhir.r4.model.Linkage.LinkageType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Linkage;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import ca.uhn.fhir.jpa.starter.AppTestMockPermissionRegistry;
import ca.uhn.fhir.jpa.starter.AppTestMockPrincipalRegistry;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import junit.framework.AssertionFailedError;

@ActiveProfiles( "auth-aware-test")
public class PatientAppClientTest extends BaseSuppplementalDataStoreTest {

	/*
	 * Use Case: retrieve metadata
	 * 
	 * Use Case: store new LOCAL Condition/Goal/Observation resources and retrieve them
	 */

	@Autowired
	AppTestMockPrincipalRegistry mockPrincipalRegistry ;
	
	@Autowired
	AppTestMockPermissionRegistry mockPermissionRegistry ;
	
	private static final String FOREIGN_PARTITION_NAME = "http://my.ehr.org/fhir/R4/" ;

	private IIdType authorizedPatientId;
	private IGenericClient patientAppClient ;

	@BeforeEach
	public void setupAuthorizedPatient() {
		authorizedPatientId = new IdType( FOREIGN_PARTITION_NAME, "Patient", createTestSpecificId(), null );
		String token = mockPrincipalRegistry.register().principal( "MyPatient", authorizedPatientId.toString() ).token() ;

		patientAppClient = authenticatingClient( token ) ;
	}

	@Test
	void canFetchOAuth2Metadata() {
		// no authorization attached
		IGenericClient patientAppClient = client() ;

		CapabilityStatement cap = patientAppClient.capabilities().ofType( CapabilityStatement.class ).execute() ;

		Assertions.assertNotNull( cap );

		assertThat( cap.getRest().size(), greaterThanOrEqualTo(1) ) ;
		CapabilityStatementRestComponent rest = cap.getRest().get(0) ;

		CapabilityStatementRestSecurityComponent security = rest.getSecurity() ;

		Extension extOauthUris =
			security.getExtension().stream()
				.filter( ext -> "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris".equals(ext.getUrl()) )
				.findFirst()
				.orElseThrow( () -> new AssertionFailedError("no REST extension for \"http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris\"") )
				;
		Extension extAuthorize =
			extOauthUris.getExtension().stream()
				.filter( ext -> "authorize".equals(ext.getUrl()) )
				.findFirst()
				.orElseThrow( () -> new AssertionFailedError("no REST extension for \"authorize\"") )
				;
		Extension extToken =
			extOauthUris.getExtension().stream()
				.filter( ext -> "token".equals(ext.getUrl()) )
				.findFirst()
				.orElseThrow( () -> new AssertionFailedError("no REST extension for \"token\"") )
				;

		Type authorizeValue = extAuthorize.getValue();
		assertThat( authorizeValue, instanceOf(UriType.class) );
		assertThat( ((UriType)authorizeValue).asStringValue(), equalTo("http://my.ehr.org/oauth2/authorize") ) ;
		
		Type tokenValue = extToken.getValue();
		assertThat( tokenValue, instanceOf(UriType.class) );
		assertThat( ((UriType)tokenValue).asStringValue(), equalTo("http://my.ehr.org/oauth2/token") ) ;
	}
	
	@Test
	void canStoreQuestionnaireWhereSubjectIsAuthorizedPatientWithoutAdditionalSetup() {
		String questId = createTestSpecificId();
		
		QuestionnaireResponse questionnaireResponse  = new QuestionnaireResponse() ;
		questionnaireResponse.setSubject( new Reference( authorizedPatientId ) ) ;
		questionnaireResponse.setQuestionnaire( questId ) ;
		IIdType questRespId = patientAppClient.create().resource(questionnaireResponse).execute().getId();
		
		QuestionnaireResponse readQuestResp = patientAppClient.read().resource(QuestionnaireResponse.class).withId(questRespId).execute();
		
		Assertions.assertNotNull( readQuestResp );
	}

	private IIdType queryLocalPatientId() {
		List<Linkage> linkages =
			patientAppClient.search()
				.forResource( Linkage.class )
				.where( Linkage.ITEM.hasId( authorizedPatientId.toUnqualifiedVersionless() ) )
				.returnBundle(Bundle.class).execute()
				.getEntry().stream()
					.filter( BundleEntryComponent::hasResource )
					.map( BundleEntryComponent::getResource )
					.filter( Linkage.class::isInstance )
					.map( Linkage.class::cast )
					.collect( toList() )
					;

		IIdType patientId =
			linkages.stream()
				.flatMap( k -> k.getItem().stream() )
				.filter( i -> i.getType() == LinkageType.SOURCE )
				.map( LinkageItemComponent::getResource )
				.map( Reference::getReferenceElement )
				.findFirst()
				.orElseThrow( () -> new AssertionFailedError("expected a Patient source link") )
				;
		return patientId ;
	}

	@Test
	@Disabled("OBSOLETE REQUIREMENT: see #PatientLinkingTest")
	void canRetrieveLinkedPatientFromLocalPartition() {
		IIdType localPatientId = queryLocalPatientId() ;

		Assertions.assertNotNull( localPatientId );

		Patient patientResp = patientAppClient.read().resource(Patient.class).withId(localPatientId).execute();

		Assertions.assertNotNull( patientResp );
		Assertions.assertEquals( patientResp.getIdElement().getIdPart(), localPatientId.getIdPart() );

		Set<IIdType> relatedAlternatePatientIds =
			patientAppClient.search()
				.forResource( Linkage.class )
				.where( Linkage.ITEM.hasId( localPatientId) )
				.returnBundle(Bundle.class).execute()
				.getEntry().stream()
					.filter( BundleEntryComponent::hasResource )
					.map( BundleEntryComponent::getResource )
					.filter( Linkage.class::isInstance )
					.map( Linkage.class::cast )
					.map( Linkage::getItem )
					.flatMap( List::stream )
					.filter( i -> i.getType() == LinkageType.ALTERNATE )
					.filter( Linkage.LinkageItemComponent::hasResource )
					.map( Linkage.LinkageItemComponent::getResource )
					.map( Reference::getReferenceElement )
					.collect( toSet() )
					;

		Assertions.assertEquals( relatedAlternatePatientIds.size(), 1 );
		IIdType relatedAlternatePatientId = relatedAlternatePatientIds.iterator().next() ;
		Assertions.assertEquals( relatedAlternatePatientId.getIdPart(), authorizedPatientId.getIdPart() );
	}

	@Test
	@Disabled("OBSOLETE REQUIREMENT: see #PatientLinkingTest")
	void canStoreQuestionnaireWhereSubjectIsLocalPatientWithoutAdditionalSetup() {
		IIdType localPatientId = queryLocalPatientId() ;
		
		String questId = createTestSpecificId();
		
		QuestionnaireResponse questionnaireResponse  = new QuestionnaireResponse() ;
		questionnaireResponse.setSubject( new Reference( localPatientId ) ) ;
		questionnaireResponse.setQuestionnaire( questId ) ;
		IIdType questRespId = patientAppClient.create().resource(questionnaireResponse).execute().getId();
		
		QuestionnaireResponse readQuestResp = patientAppClient.read().resource(QuestionnaireResponse.class).withId(questRespId).execute();
		
		Assertions.assertNotNull( readQuestResp );
	}
	
	@Test
	@Disabled("OBSOLETE REQUIREMENT: see #PatientLinkingTest")
	void cannotStoreQuestionnaireWhereSubjectIsNotAuthorizedPatient() {
		Reference nonAuthorizedPatient = new Reference( new IdType( "Patient", createTestSpecificId() ) );
		String questId = createTestSpecificId();
		
		QuestionnaireResponse questionnaireResponse  = new QuestionnaireResponse() ;
		questionnaireResponse.setSubject( nonAuthorizedPatient ) ;
		questionnaireResponse.setQuestionnaire( questId ) ;
		
		ForbiddenOperationException exception =
			assertThrows( ForbiddenOperationException.class, () -> {
				patientAppClient.create().resource(questionnaireResponse).execute().getId();
			} );
		
		assertThat( exception.getMessage(), containsString( "Access denied by rule: no access rules grant permission" ) ) ;
	}

	private Condition createHealthConcern( Reference subjectRef, String codeAsPlainText ) {
		Condition condition  = new Condition() ;
		condition.setSubject( subjectRef ) ;
		
		CodeableConcept healthConcernCategory = new CodeableConcept();
		healthConcernCategory.addCoding( new Coding( "http://hl7.org/fhir/ValueSet/condition-category", "health-concern", "health concern" ) ) ;
		condition.setCategory( asList( healthConcernCategory ) ) ;
		
		condition.setCode( healthConcernCategory.setText(codeAsPlainText) ) ;
		
		return condition ;
	}
	
	@Test
	void canStoreConditionWhereSubjectIsAuthorizedPatientWithoutAdditionalSetup() {
		Condition condition  = createHealthConcern( new Reference( authorizedPatientId ), "my health concern" ) ;
		
		IIdType conditionId = patientAppClient.create().resource(condition).execute().getId();
		
		Condition readCondition = patientAppClient.read().resource(Condition.class).withId(conditionId).execute();
		
		Assertions.assertNotNull( readCondition );
		
		Bundle searchConditionBundle = patientAppClient.search().forResource(Condition.class).where( Condition.SUBJECT.hasId(authorizedPatientId)).returnBundle(Bundle.class).execute();

		List<Condition> searchConditions =
			searchConditionBundle.getEntry().stream()
				.filter( Bundle.BundleEntryComponent::hasResource )
				.map( Bundle.BundleEntryComponent::getResource )
				.filter( Condition.class::isInstance )
				.map( Condition.class::cast )
				.collect( toList() )
				;
		
		Assertions.assertEquals( searchConditions.size(), 1 );

		Condition searchCondition = searchConditions.iterator().next() ;

		Assertions.assertEquals( searchCondition.getIdElement().getIdPart(), conditionId.getIdPart() );
	}

	
	/*
	 * Scenario: "log in as Nancy Smart (related to Timmy) and then access records for Barbara"
	 * c.f. authorize_response_relatedperson.json
	 * c.f. id_token_relatedperson.json
	 */
	
	@Test
	@Disabled("OBSOLETE REQUIREMENT: see #PatientLinkingTest")
	void cannotStoreConditionWhereSubjectIsUnrelatedToAuthorizedUser() {
		IIdType otherPatientId = new IdType( FOREIGN_PARTITION_NAME, "Patient", createTestSpecificId(), null );
		IIdType authorizedRelatedPersonId = new IdType( "RelatedPerson", createTestSpecificId() ) ;
		String token = mockPrincipalRegistry.register().principal( "MyRelatedPerson", authorizedRelatedPersonId.toString() ).token() ;
		mockPermissionRegistry
			.person( otherPatientId.toString() )
			.permitsPerson( authorizedRelatedPersonId.toString() )
			.toReadAndWrite()
			;
		
		IGenericClient patientAppClient = authenticatingClient( token ) ;
		
		Reference authorizedPatient = new Reference( authorizedPatientId );
		
		Condition condition  = createHealthConcern( authorizedPatient, "my health concern" ) ;
		
		assertThrows( ForbiddenOperationException.class, () -> {
			patientAppClient.create().resource(condition).execute().getId();
		});
	}
	

	/*
	 * Scenario: "log in as Nancy Smart (related to Timmy) and then access records for Timmy"
	 * c.f. authorize_response_relatedperson.json
	 * c.f. id_token_relatedperson.json
	 */
	
	@Test
	void canStoreConditionWhereSubjectIsRelatedToAuthorizedUser() {
		IIdType authorizedRelatedPersonId = new IdType( FOREIGN_PARTITION_NAME, "RelatedPerson", createTestSpecificId(), null ) ;
		String token = mockPrincipalRegistry.register().principal( "MyRelatedPerson", authorizedRelatedPersonId.toString() ).token() ;
		mockPermissionRegistry
			.person( authorizedPatientId.toString() )
			.permitsPerson( authorizedRelatedPersonId.toString() )
			.toReadAndWrite()
			;
		
		IGenericClient patientAppClient = authenticatingClient( token ) ;
		
		Condition condition  = createHealthConcern( new Reference( authorizedPatientId ), "my health concern" ) ;
		
		IIdType conditionId = patientAppClient.create().resource(condition).execute().getId();
		
		Condition readConditionResp = patientAppClient.read().resource(Condition.class).withId(conditionId).execute();
		
		Assertions.assertNotNull( readConditionResp );
	}
	
}
