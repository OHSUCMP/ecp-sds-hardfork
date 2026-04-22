package edu.ohsu.cmp.ecp.sds.r4b;

import ca.uhn.fhir.jpa.starter.annotations.OnR4BCondition;
import edu.ohsu.cmp.ecp.sds.AncillaryResources;
import edu.ohsu.cmp.ecp.sds.base.BaseAncillaryResources;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4b.model.*;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Conditional(OnR4BCondition.class)
public class AncillaryResourcesR4B extends BaseAncillaryResources implements AncillaryResources {
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
