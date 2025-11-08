// SPDX-License-Identifier: MPL-2.0
package org.winlogon.fastrtp;

import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

public class RtpLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        var resolver = new MavenLibraryResolver();

        var dependencies = new Dependency[] {
            dependency("org.winlogon", "asynccraftr", "0.1.0"),
            dependency("com.github.walker84837", "JResult", "1.4.0"),
        };
        var repositories = new RemoteRepository[] {
            repository("central", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR),
            repository("jitpack", "https://jitpack.io"),
            repository("winlogon", "https://maven.winlogon.org/releases"),
        };

        for (var repository : repositories) {
            resolver.addRepository(repository);
        }

        for (var dependency : dependencies) {
            resolver.addDependency(dependency);
        }

        classpathBuilder.addLibrary(resolver);
    }

    private RemoteRepository repository(String id, String url) {
        return new RemoteRepository.Builder(id, "default", url).build();
    }

    private Dependency dependency(String groupId, String artifactId, String version) {
        return new Dependency(
            new DefaultArtifact(groupId + ":" + artifactId + ":" + version),
            null
        );
    }
}
