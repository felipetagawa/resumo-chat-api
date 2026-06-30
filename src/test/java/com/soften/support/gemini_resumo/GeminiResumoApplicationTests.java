package com.soften.support.gemini_resumo;

import com.soften.support.gemini_resumo.repositorys.CalledRepository;
import com.soften.support.gemini_resumo.repositorys.PreControlRepositoy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
		"gemini.api.key=test-key",
		"spring.flyway.enabled=false",
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class GeminiResumoApplicationTests {

	@MockBean
	private CalledRepository calledRepository;

	@MockBean
	private PreControlRepositoy preControlRepositoy;

	@Test
	void contextLoads() {
	}

}
