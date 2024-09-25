package edu.ohsu.cmp.ecp.sds;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.batch2.jobs.expunge.DeleteExpungeProvider;
import ca.uhn.fhir.jpa.starter.AppProperties;
import ca.uhn.fhir.rest.server.RestfulServer;

@Configuration
public class DeleteExpungeConfig {

	@Inject
	RestfulServer server ;

	@Inject
	AppProperties appProperties ;

	@Inject
	DeleteExpungeProvider deleteExpungeProvider ;

	@PostConstruct
	void configureDeleteExpunge() {

		if (appProperties.getDelete_expunge_enabled()) {
			server.registerProvider( deleteExpungeProvider );
		}

	}
}
