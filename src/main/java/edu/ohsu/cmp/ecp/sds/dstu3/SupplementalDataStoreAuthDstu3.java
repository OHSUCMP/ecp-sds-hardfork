package edu.ohsu.cmp.ecp.sds.dstu3;

import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.instance.model.api.IIdType;

import java.net.URI;

import org.hl7.fhir.dstu3.model.CapabilityStatement;
import org.hl7.fhir.dstu3.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.UriType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.starter.annotations.OnDSTU3Condition;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreAuthBase;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStorePermissions;

@Component
@Conditional(OnDSTU3Condition.class)
public class SupplementalDataStoreAuthDstu3 extends SupplementalDataStoreAuthBase {

	public SupplementalDataStoreAuthDstu3(SupplementalDataStorePermissions permissions) {
		super( permissions );
	}

	@Override
	protected IIdType idFromSubject(String subject) {
		return new IdType(subject.toString());
	}

	@Override
	protected IIdType idFromContextParameter( String resourceType, String contextParameterValue ) {
		return new IdType(contextParameterValue.toString()).withResourceType(resourceType);
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
