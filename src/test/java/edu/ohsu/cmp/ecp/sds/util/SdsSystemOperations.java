package edu.ohsu.cmp.ecp.sds.util;

import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.provider.ProviderConstants;

public class SdsSystemOperations {
	private final IGenericClient client ;

	public SdsSystemOperations( IGenericClient client ) {
		this.client = client ;
	}

	/*
	 * https://smilecdr.com/docs/fhir_repository/deleting_data.html#expunge
	 * e.g. POST [base]/<resourceType>/<resourceId>/$expunge ( expungeDeletedResources=true, expungePreviousVersions=true)
	 */
	public IBaseParameters expungeOperation( IIdType resourceId ) {
		IBaseParameters operationResult =
			client
				.operation()
				.onInstance( resourceId )
				.named( ProviderConstants.OPERATION_EXPUNGE )
				.withParameters(
					new Parameters()
						.addParameter( "expungeDeletedResources", new BooleanType(true) )
						.addParameter( "expungePreviousVersions", new BooleanType(true) )
						.addParameter( "_cascade", new StringType("delete") )
					)
				.execute()
				;
//		assertThat( methodOutcome.getId(), notNullValue() ) ;
//		assertNoProblemIssues( methodOutcome ) ;
//		printIssues( methodOutcome ) ;
		return operationResult ;
	}

	/*
	 * https://smilecdr.com/docs/fhir_repository/deleting_data.html#expunge
	 * e.g. POST [base]/$expunge ( expungeDeletedResources=true, expungePreviousVersions=true)
	 */
	public IBaseParameters expungeOperation() {
		IBaseParameters operationResult =
			client
				.operation()
				.onServer()
				.named( ProviderConstants.OPERATION_EXPUNGE )
				.withParameters(
					new Parameters()
						.addParameter( "expungeDeletedResources", new BooleanType(true) )
						.addParameter( "expungePreviousVersions", new BooleanType(true) )
						//.addParameter( "_cascade", new StringType("delete") )
				)
				.execute()
				;
		return operationResult ;
	}

	/*
	 * https://smilecdr.com/docs/fhir_repository/deleting_data.html#expunge
	 * e.g. POST [base]/$expunge ( expungeEverything=true)
	 */
	public IBaseParameters expungeEverythingOperation() {
		IBaseParameters operationResult =
				client
				.operation()
				.onServer()
				.named( ProviderConstants.OPERATION_EXPUNGE )
				.withParameters(
					new Parameters()
						.addParameter( "expungeEverything", new BooleanType(true) )
				)
				.execute()
				;
		return operationResult ;
	}

}
