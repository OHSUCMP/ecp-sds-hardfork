package edu.ohsu.cmp.ecp.sds.dstu2;

import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.instance.model.api.IIdType;

import java.net.URI;

import org.hl7.fhir.dstu2.model.Conformance;
import org.hl7.fhir.dstu2.model.Conformance.ConformanceRestComponent;
import org.hl7.fhir.dstu2.model.Extension;
import org.hl7.fhir.dstu2.model.IdType;
import org.hl7.fhir.dstu2.model.UriType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.starter.annotations.OnDSTU2Condition;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStoreAuthBase;
import edu.ohsu.cmp.ecp.sds.SupplementalDataStorePermissions;

@Component
@Conditional(OnDSTU2Condition.class)
public class SupplementalDataStoreAuthDstu2 extends SupplementalDataStoreAuthBase {

	public SupplementalDataStoreAuthDstu2(SupplementalDataStorePermissions permissions) {
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
		Conformance cs = (Conformance) theCapabilityStatement;
		
		for ( ConformanceRestComponent rest : cs.getRest() ) {
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
