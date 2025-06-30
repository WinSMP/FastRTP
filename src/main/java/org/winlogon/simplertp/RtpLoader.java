package org.winlogon.simplertp;

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

        resolver.addRepository(
            new RemoteRepository.Builder(
                "central",
                "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
            ).build()
        );

        resolver.addRepository(
            new RemoteRepository.Builder(
                "winlogon",
                "default",
                "https://maven.winlogon.org/releases"
            ).build()
        );

        resolver.addDependency(
            new Dependency(
                new DefaultArtifact("org.winlogon:asynccraftr:0.1.0"),
                null
            )
        );

        classpathBuilder.addLibrary(resolver);
    }
}
