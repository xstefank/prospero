/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.it.commonapi;

import org.jboss.galleon.ProvisioningException;
import org.junit.Assert;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.After;
import org.junit.Test;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.test.MetadataTestUtils;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InstallationHistoryActionTest extends WfCoreTestBase {

    private Path channelsFile;

    @After
    public void tearDown() throws Exception {
        if (Files.exists(channelsFile)) {
            Files.delete(channelsFile);
        }
    }

    @Test
    public void listUpdates() throws Exception {
        // installCore
        channelsFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        // updateCore
        MetadataTestUtils.prepareProvisionConfig(outputPath.resolve(MetadataTestUtils.PROVISION_CONFIG_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);
        updateAction().performUpdate();

        // get history
        List<SavedState> states = new InstallationHistoryAction(outputPath, new AcceptingConsole()).getRevisions();

        // assert two entries
        assertEquals(2, states.size());
    }

    @Test
    public void rollbackChanges() throws Exception {
        channelsFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);
        final Path modulesPaths = outputPath.resolve(Paths.get("modules", "system", "layers", "base"));
        final Path wildflyCliModulePath = modulesPaths.resolve(Paths.get("org", "jboss", "as", "cli", "main"));

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        MetadataTestUtils.prepareProvisionConfig(outputPath.resolve(MetadataTestUtils.PROVISION_CONFIG_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);
        updateAction().performUpdate();
        Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Updated jar should be present in module", wildflyCliModulePath.resolve(UPGRADE_JAR).toFile().exists());

        final InstallationHistoryAction historyAction = new InstallationHistoryAction(outputPath, new AcceptingConsole());
        final List<SavedState> revisions = historyAction.getRevisions();

        final SavedState savedState = revisions.get(1);
        historyAction.rollback(savedState, mavenSessionManager, Collections.emptyList());

        wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Reverted jar should be present in module", wildflyCliModulePath.resolve(BASE_JAR).toFile().exists());
    }

    @Test
    public void rollbackChangesWithTemporaryRepo() throws Exception {
        // install server
        final Path manifestPath = temp.newFile().toPath();
        channelsFile = temp.newFile().toPath();
        MetadataTestUtils.copyManifest("channels/wfcore-19-base.yaml", manifestPath);
        MetadataTestUtils.prepareProvisionConfig(channelsFile, List.of(manifestPath.toUri().toURL()));

        final Path modulesPaths = outputPath.resolve(Paths.get("modules", "system", "layers", "base"));
        final Path wildflyCliModulePath = modulesPaths.resolve(Paths.get("org", "jboss", "as", "cli", "main"));

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        MetadataTestUtils.upgradeStreamInManifest(manifestPath, resolvedUpgradeArtifact);
        updateAction().performUpdate();
        Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Updated jar should be present in module", wildflyCliModulePath.resolve(UPGRADE_JAR).toFile().exists());

        final InstallationHistoryAction historyAction = new InstallationHistoryAction(outputPath, new AcceptingConsole());
        final List<SavedState> revisions = historyAction.getRevisions();
        final SavedState savedState = revisions.get(1);

        // perform the rollback using temporary repository only. Offline mode disables other repositories
        final URL temporaryRepo = mockTemporaryRepo();
        final MavenSessionManager offlineSessionManager = new MavenSessionManager(Optional.empty(), true);
        historyAction.rollback(savedState, offlineSessionManager, List.of(new Repository("temp-repo", temporaryRepo.toExternalForm())));

        wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Reverted jar should be present in module", wildflyCliModulePath.resolve(BASE_JAR).toFile().exists());
        assertThat(ProsperoConfig.readConfig(outputPath.resolve(MetadataTestUtils.PROVISION_CONFIG_FILE_PATH)).getChannels())
                .withFailMessage("Temporary repository should not be listed")
                .flatMap(Channel::getRepositories)
                .map(Repository::getUrl)
                .doesNotContain(temporaryRepo.toExternalForm());
    }

    @Test
    public void displayChanges() throws Exception {
        channelsFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        MetadataTestUtils.prepareProvisionConfig(outputPath.resolve(MetadataTestUtils.PROVISION_CONFIG_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);
        updateAction().performUpdate();

        final InstallationHistoryAction historyAction = new InstallationHistoryAction(outputPath, new AcceptingConsole());
        final List<SavedState> revisions = historyAction.getRevisions();

        final SavedState savedState = revisions.get(1);
        final List<ArtifactChange> changes = historyAction.compare(savedState);

        for (ArtifactChange change : changes) {
            System.out.println(change);
        }

        assertEquals(1, changes.size());

        Map<String, String[]> expected = new HashMap<>();
        expected.put("org.wildfly.core:wildfly-cli", new String[]{BASE_VERSION, UPGRADE_VERSION});

        for (ArtifactChange change : changes) {
            if (expected.containsKey(change.getArtifactName())) {
                final String[] versions = expected.get(change.getArtifactName());
                assertEquals(versions[1], change.getNewVersion().get());
                expected.remove(change.getArtifactName());
            } else {
                Assert.fail("Unexpected artifact in updates " + change);
            }
        }
        assertEquals("Not all expected changes were listed", 0, expected.size());
    }

    private UpdateAction updateAction() throws ProvisioningException, OperationException {
        return new UpdateAction(outputPath, mavenSessionManager, new AcceptingConsole(), Collections.emptyList());
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws IOException {
        final File manifestFile = outputPath.resolve(MetadataTestUtils.MANIFEST_FILE_PATH).toFile();
        return ManifestYamlSupport.parse(manifestFile).getStreams()
                .stream().filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst()
                .map(s->new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion()));
    }
}
