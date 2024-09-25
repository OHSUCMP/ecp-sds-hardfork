package edu.ohsu.cmp.ecp.sds;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpUriRequest;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle ;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Linkage ;
import org.hl7.fhir.r4.model.Patient ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import ca.uhn.fhir.jpa.starter.AppTestMockPrincipalRegistry;
import ca.uhn.fhir.rest.api.MethodOutcome;

@ActiveProfiles( "auth-aware-test")
public class CorsOptionTest extends BaseSuppplementalDataStoreTest {

	private static final String FOREIGN_PARTITION_NAME = "http://my.ehr.org/fhir/R4/" ;

	@Autowired
	AppTestMockPrincipalRegistry mockPrincipalRegistry ;

	@Autowired
	private SupplementalDataStoreProperties sdsProperties ;

	private IIdType authorizedPatientId ;

	private String authToken ;

	@BeforeEach
	public void setupAuthorization() {
		authorizedPatientId = new IdType( FOREIGN_PARTITION_NAME, "Patient", createTestSpecificId(), null ) ;
		authToken = mockPrincipalRegistry.register().principal( "MyPatient", authorizedPatientId.toString() ).token() ;
	}

	private <T extends HttpUriRequest> T authorize( T request ) {
		request.addHeader( HttpHeaders.AUTHORIZATION, "Bearer " + authToken ) ;
		return request ;
	}

	@Test
	public void doesIncludePartitionHeaderInCorsOptionsForCapabilityStatement() {
		CapabilityStatement cap = client().capabilities().ofType( CapabilityStatement.class ).execute() ;
		assertThat( cap, notNullValue() ) ;

		HttpResponse resp2 = executeRequest( HttpGet::new, "metadata" ) ;
		assertThat( resp2.getStatusLine().getStatusCode(), equalTo( 200 ) ) ;

		doesIncludePartitionHeaderInCorsOptions( "GET", "metadata" );
	}

	@Test
	public void doesIncludePartitionHeaderInCorsOptionsForLinkages() {
		Bundle linkageBundle =
			authenticatingClient( authToken )
				.search()
				.forResource( Linkage.class )
				.where( Linkage.ITEM.hasId( authorizedPatientId.toUnqualifiedVersionless() ) )
				.returnBundle( Bundle.class )
				.execute()
				;
		assertThat( linkageBundle, notNullValue() ) ;

		doesIncludePartitionHeaderInCorsOptions( "GET", "Linkage?item=" + authorizedPatientId.toUnqualifiedVersionless() );
	}

	@Test
	public void doesIncludePartitionHeaderInCorsOptionsForLinkagesInForeignPartition() {
		Bundle linkageBundle =
			authenticatingClientTargetingPartition( authToken, FOREIGN_PARTITION_NAME )
				.search()
				.forResource( Linkage.class )
				.where( Linkage.ITEM.hasId( authorizedPatientId.toUnqualifiedVersionless() ) )
				.returnBundle( Bundle.class )
				.execute()
				;
		assertThat( linkageBundle, notNullValue() ) ;

		doesIncludePartitionHeaderInCorsOptions( "GET", "Linkage?item=" + authorizedPatientId.toUnqualifiedVersionless() );
	}

	@Test
	public void doesIncludePartitionHeaderInCorsOptionsForPatientInForeignPartition() {
		MethodOutcome outcome =
			authenticatingClientTargetingPartition( authToken, FOREIGN_PARTITION_NAME )
				.update()
				.resource( new Patient().setId( authorizedPatientId.toUnqualifiedVersionless() ) )
				.execute()
				;
		assertThat( outcome, notNullValue() ) ;
		
		doesIncludePartitionHeaderInCorsOptions( "PUT", "Patient/" + authorizedPatientId.getIdPart() );
	}
	
	private void doesIncludePartitionHeaderInCorsOptions( String requestMethod, String relativePath ) {
		final String sdsPartitionHeaderName = sdsProperties.getPartition().getHttpHeader();

		HttpResponse resp = executeRequest( corsRequest(requestMethod), relativePath ) ;
		assertThat( resp.getStatusLine().getStatusCode(), equalTo( 200 ) ) ;
		List<String> valuesOfAccessControlAllowHeaders =
			Arrays.stream( resp.getHeaders( "Access-Control-Allow-Headers" ) )
				.map( Header::getValue )
				.map( v -> v.split(",") )
				.flatMap( Arrays::stream )
				.map( String::trim )
				.map( String::toLowerCase )
				.collect( toList() ) ;
		assertThat( valuesOfAccessControlAllowHeaders, hasItem( sdsPartitionHeaderName.toLowerCase() ) ) ;
	}

	private Function<URI,HttpOptions> corsRequest( String requestMethod ) {
		final String sdsPartitionHeaderName = sdsProperties.getPartition().getHttpHeader();

		return uri -> {
			HttpOptions optionsRequest = new HttpOptions( uri ) ;
			optionsRequest.addHeader( "Origin", "https://10.0.1.1" ) ;
			optionsRequest.addHeader( "Access-Control-Request-Method", requestMethod ) ;
			optionsRequest.addHeader( "Access-Control-Request-Headers", "content-type, authorization, " + sdsPartitionHeaderName ) ;
			return optionsRequest ;
		} ;
	}
}
