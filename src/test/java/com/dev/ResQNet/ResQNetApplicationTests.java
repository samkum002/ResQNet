package com.dev.ResQNet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.mongodb.uri=mongodb://localhost:27017/ResQNetTestdb?replicaSet=rs0",
		"spring.data.mongodb.auto-index-creation=false"
})
class ResQNetApplicationTests {

	@Test
	void contextLoads() {
	}

}
