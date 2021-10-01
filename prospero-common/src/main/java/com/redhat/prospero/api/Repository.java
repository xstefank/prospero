package com.redhat.prospero.api;

import java.io.File;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.eclipse.aether.artifact.Artifact;

public interface Repository {

    File resolve(Artifact artifact) throws ArtifactNotFoundException;
    Artifact findLatestVersionOf(Artifact artifact);

    VersionRangeResult getVersionRange(Artifact artifact) throws MavenUniverseException;
}
