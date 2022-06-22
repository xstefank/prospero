package org.wildfly.prospero.cli.commands;

import java.nio.file.Path;
import java.util.Optional;

import org.jboss.galleon.ProvisioningException;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.InstallationHistory;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.Messages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

@CommandLine.Command(
        name = "revert",
        description = "Reverts to a previous installation state.",
        sortOptions = false
)
public class RevertCommand extends AbstractCommand {

    @CommandLine.Option(names = "--dir", required = true)
    Path directory;

    @CommandLine.Option(names = "--revision", required = true)
    String revision;

    @CommandLine.Option(names = "--local-repo")
    Optional<Path> localRepo;

    @CommandLine.Option(names = "--offline")
    boolean offline;

    public RevertCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        if (offline && localRepo.isEmpty()) {
            console.error(Messages.offlineModeRequiresLocalRepo());
            return ReturnCodes.INVALID_ARGUMENTS;
        }

        try {
            final MavenSessionManager mavenSessionManager;
            if (localRepo.isEmpty()) {
                mavenSessionManager = new MavenSessionManager();
            } else {
                mavenSessionManager = new MavenSessionManager(localRepo.get().toAbsolutePath());
            }
            mavenSessionManager.setOffline(offline);
            InstallationHistory installationHistory = actionFactory.history(directory.toAbsolutePath(), console);
            installationHistory.rollback(new SavedState(revision), mavenSessionManager);
        } catch (ProvisioningException e) {
            console.error("Error while executing update: " + e.getMessage());
            return ReturnCodes.PROCESSING_ERROR;
        }

        return ReturnCodes.SUCCESS;
    }
}