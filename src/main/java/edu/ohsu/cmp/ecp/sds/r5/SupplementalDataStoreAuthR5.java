package edu.ohsu.cmp.ecp.sds.r5;

import java.net.URI;

import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.UriType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.starter.annotations.OnR5Condition;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreAuthBase;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStorePermissions;

@Component
@Conditional(OnR5Condition.class)
public class SupplementalDataStoreAuthR5 extends SupplementalDataStoreAuthBase {

	public SupplementalDataStoreAuthR5(SupplementalDataStorePermissions permissions) {
		super( permissions );
	}

	@Override
	protected IIdType idFromSubject(String subject) {
		return new IdType(subject.toString());
	}

	@Override
	protected IIdType idFromContextParameter( String contextParameterValue ) {
		return new IdType(contextParameterValue.toString());
	}

	@Override
	public void addAuthCapability(IBaseConformance theCapabilityStatement, URI authorizeUri, URI tokenUri ) {
		CapabilityStatement cs = (CapabilityStatement) theCapabilityStatement;
		
		for ( CapabilityStatementRestComponent rest : cs.getRest() ) {
			Extension extensionOAuthUris = rest.getSecurity().addExtension() ;
			extensionOAuthUris.setUrl( "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris" ) ;

			Extension extensionAuthorize = extensionOAuthUris.addExtension() ;
			extensionAuthorize.setUrl( "authorize" ) ;
			extensionAuthorize.setValue( new UriType( authorizeUri ) ) ;
			
			Extension extensionToken = extensionOAuthUris.addExtension() ;
			extensionToken.setUrl( "token" ) ;
			extensionToken.setValue( new UriType( tokenUri ) ) ;
		}

	}
}
