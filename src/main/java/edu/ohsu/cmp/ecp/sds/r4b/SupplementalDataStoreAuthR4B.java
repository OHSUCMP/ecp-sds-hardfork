package edu.ohsu.cmp.ecp.sds.r4b;

import java.net.URI;

import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4b.model.CapabilityStatement;
import org.hl7.fhir.r4b.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r4b.model.Extension;
import org.hl7.fhir.r4b.model.IdType;
import org.hl7.fhir.r4b.model.UriType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.starter.annotations.OnR4BCondition;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreAuthBase;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStorePermissions;

@Component
@Conditional(OnR4BCondition.class)
public class SupplementalDataStoreAuthR4B extends SupplementalDataStoreAuthBase {

	public SupplementalDataStoreAuthR4B(SupplementalDataStorePermissions permissions) {
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
