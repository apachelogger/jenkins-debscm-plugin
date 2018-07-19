package jenkins.plugins.deb;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.*;
import hudson.util.ArgumentListBuilder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

import static java.lang.String.format;

public class DebSCM extends SCM {
    public String repo;
    public String source;

    @DataBoundConstructor
    public DebSCM(String repo, String source) {
        this.repo = repo;
        this.source = source;
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        logger.info(new Exception().getStackTrace()[0].getMethodName());
        return true;
    }

    // FIXME: need to track repo and source. if either changes we need to always poll!!!!!!!!

    @Override
    public void checkout(Run<?,?> build,
                            Launcher launcher,
                            FilePath workspace,
                            TaskListener listener,
                            File changelogFile,
                            SCMRevisionState baseline)
            throws IOException, InterruptedException {
        logger.info(new Exception().getStackTrace()[0].getMethodName());
        logger.info(format("baseline %s, repo '%s', source '%s'", baseline, repo, source));

        FilePaths paths = new FilePaths(workspace);
        FilePath root = paths.root();

        for (FilePath content : root.list()) {
            if (content.isDirectory()) {
                continue;
            }
            logger.warning("Deleting " + content.getRemote());
            content.delete();
        }

        paths.aptETC().child("sources.list").write(repo, null);

        ArgumentListBuilder updateArgs = new ArgumentListBuilder();
        updateArgs.add("apt-get", "update");
        updateArgs.add(paths.standardAPTOptions());
        int code = launcher.launch().cmds(updateArgs).pwd(root).stdout(listener).stderr(listener.getLogger()).join();
        if (code != 0) {
            new Exception("Failed to apt update");
        }

        ArgumentListBuilder srcArgs = new ArgumentListBuilder();
        srcArgs.add("apt-get", "source");
        srcArgs.add(paths.standardAPTOptions());
        srcArgs.add("--download-only", source);
        code = launcher.launch().cmds(srcArgs).pwd(root).stdout(listener).stderr(listener.getLogger()).join();
        if (code != 0) {
            new Exception("Failed to get source");
        }

        FilePath meta = paths.meta();
        // TODO: raise if more than one dsc. we clean up before we download, so there must only be one!
        for (FilePath path : root.list("*.dsc")) {
            logger.info(path.toURI().toString());
            BufferedReader br = new BufferedReader(new InputStreamReader(path.read()));
            String line;
            while ((line = br.readLine()) != null) {
//                logger.warning(line);
                String []parts = line.split(": ", 2);
                if (parts[0].equals("Version")) {
                    logger.info("Writing current: " + parts[1]);
                    meta.child("current").write(parts[1], null);
                    break;
                }
            }
        }
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> build,
                                                   FilePath workspace,
                                                   Launcher launcher,
                                                   TaskListener listener)
            throws IOException, InterruptedException {
        logger.info(new Exception().getStackTrace()[0].getMethodName());
        FilePath current = new FilePaths(workspace).meta().child("current");
        if (!current.exists()) {
            return SCMRevisionState.NONE;
        }
        logger.info("Version: " + current.readToString());
        return new DebSCMRevisionState(current.readToString());
    }

    class VersionComperator implements Comparator<String> {
        private Launcher launcher;

        VersionComperator(Launcher launcher) {
            this.launcher = launcher;
        }

        @Override
        public int compare(String o1, String o2) {
            if (o1.equals(o2)) {
                return 0;
            }
            try {
                if (0 == launcher.launch().cmds("dpkg", "--compare-versions", o1, "lt", o2).join()) {
                    return -1;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    @Override
    public PollingResult compareRemoteRevisionWith(Job<?, ?> project,
                                                   Launcher launcher,
                                                   FilePath workspace,
                                                   TaskListener listener,
                                                   SCMRevisionState baseline)
            throws IOException, InterruptedException {
        logger.info(new Exception().getStackTrace()[0].getMethodName());
        logger.info(format("project: %s", project.getDisplayName()));

        listener.getLogger().println("[poll] baseline: " + baseline);

        FilePaths paths = new FilePaths(workspace);

        FilePath root = paths.root();

        paths.aptETC().child("sources.list").write(repo, null);

        ArgumentListBuilder updateArgs = new ArgumentListBuilder();
        updateArgs.add("apt-get", "update");
        updateArgs.add(paths.standardAPTOptions());
        int code = launcher.launch().cmds(updateArgs).pwd(root).stdout(listener).stderr(listener.getLogger()).join();
        if (code != 0) {
            new Exception("Failed to apt update");
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("apt-cache", "showsrc");
        args.add(paths.standardAPTOptions());
        args.add("--only-source", source);
        code = launcher.launch().cmds(args).stdout(stdout).stderr(listener.getLogger()).join();
        if (code != 0) {
            new Exception("Failed to showsrc");
        }

        List<String> versions = new Vector<String>();
        BufferedReader br = new BufferedReader(new StringReader(stdout.toString()));
        String line;
        while ((line = br.readLine()) != null) {
            String []parts = line.split(": ", 2);
            if (parts[0].equals("Version")) {
                versions.add(parts[1]);
            }
        }

        Collections.sort(versions, new VersionComperator(launcher));
        Collections.reverse(versions);
        String remoteVersion = versions.get(0);
        logger.info("newest remote: " + remoteVersion);

        String baseVersion = ((DebSCMRevisionState)baseline).getDebVersion();

        PollingResult.Change change = PollingResult.Change.NONE;
        switch ((new VersionComperator(launcher)).compare(remoteVersion, baseVersion)) {
            case -1:
                change = PollingResult.Change.INCOMPARABLE;
                break;
            case 0:
                change = PollingResult.Change.NONE;
                break;
            case 1:
                change = PollingResult.Change.SIGNIFICANT;
                break;
        }

        return new PollingResult(baseline, new DebSCMRevisionState(remoteVersion), change);
    }

    @Override
    public void buildEnvVars(AbstractBuild<?,?> build,
                             Map<String,String> env)
    {
        logger.info(new Exception().getStackTrace()[0].getMethodName());
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        logger.info(new Exception().getStackTrace()[0].getMethodName());
        return null;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        logger.info(new Exception().getStackTrace()[0].getMethodName());
        return DescriptorImpl.DESCRIPTOR;
    }

    public static final class DescriptorImpl extends SCMDescriptor<DebSCM> {
        @Extension
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        private DescriptorImpl() {
            super(DebSCM.class, null);
            load();
        }

        public String getDisplayName() {
            return "Deb Repo";
        }


        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            DebSCM scm = req.bindJSON(DebSCM.class, formData);
            return scm;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return true;
        }
    }

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DebSCM.class.getName());
}
