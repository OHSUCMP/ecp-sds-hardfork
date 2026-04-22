package edu.ohsu.cmp.ecp.sds.dstu3;

import ca.uhn.fhir.jpa.starter.annotations.OnDSTU3Condition;
import edu.ohsu.cmp.ecp.sds.AncillaryResources;
import edu.ohsu.cmp.ecp.sds.base.BaseAncillaryResources;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Conditional(OnDSTU3Condition.class)
public class AncillaryResourcesDstu3 extends BaseAncillaryResources implements AncillaryResources {
	@Override
	public List<Class<? extends IBaseResource>> getAncillaryResourceClasses() {
		return List.of(
			Binary.class,
			Device.class,
			Location.class,
			Medication.class,
			Organization.class,
			Practitioner.class,
			PractitionerRole.class
		);
	}
}
