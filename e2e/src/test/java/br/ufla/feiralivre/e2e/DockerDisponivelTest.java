package br.ufla.feiralivre.e2e;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

public class DockerDisponivelTest {

    @Test
    public void testcontainersDeveSubirUmContainerNoDockerLocal() {
        assertTrue(DockerClientFactory.instance().isDockerAvailable(), "O Testcontainers precisa alcançar o Docker");
        try (GenericContainer<?> nginx = new GenericContainer<>("nginx:1.27-alpine").withExposedPorts(80)) {
            nginx.start();
            assertTrue(nginx.isRunning());
        }
    }
}
