package edu.ohsu.cmp.ecp.sds.dstu2;

import ca.uhn.fhir.jpa.starter.annotations.OnDSTU2Condition;
import edu.ohsu.cmp.ecp.sds.AncillaryResources;
import edu.ohsu.cmp.ecp.sds.base.BaseAncillaryResources;
import org.hl7.fhir.dstu2.model.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Conditional(OnDSTU2Condition.class)
public class AncillaryResourcesDstu2 extends BaseAncillaryResources implements AncillaryResources {
	@Override
	public List<Class<? extends IBaseResource>> getAncillaryResourceClasses() {
		return List.of(
			Binary.class,
			Device.class,
			Location.class,
			Medication.class,
			Organization.class,
			Practitioner.class
		);
	}
}
