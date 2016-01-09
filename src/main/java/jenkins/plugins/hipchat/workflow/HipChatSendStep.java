package jenkins.plugins.hipchat.workflow;

import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import jenkins.model.Jenkins;
import jenkins.plugins.hipchat.HipChatNotifier;
import jenkins.plugins.hipchat.HipChatService;
import jenkins.plugins.hipchat.Messages;
import jenkins.plugins.hipchat.exceptions.NotificationException;
import jenkins.plugins.hipchat.model.Color;
import jenkins.plugins.hipchat.utils.BuildUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Workflow step to send a HipChat room notification.
 */
public class HipChatSendStep extends AbstractStepImpl {

    private static final Logger logger = Logger.getLogger(HipChatSendStep.class.getName());

    public final String message;

    @DataBoundSetter
    public Color color;

    @DataBoundSetter
    public String token;

    @DataBoundSetter
    public String room;

    @DataBoundSetter
    public String server;

    @DataBoundSetter
    public boolean notify;

    @DataBoundSetter
    public boolean textFormat;

    @DataBoundSetter
    public Boolean v2enabled;

    @DataBoundSetter
    public String sendAs;

    @DataBoundSetter
    public boolean failOnError;

    @DataBoundConstructor
    public HipChatSendStep(@Nonnull String message) {
        this.message = message;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(HipChatSendStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "hipchatSend";
        }

        @Override
        public String getDisplayName() {
            return Messages.HipChatSendStepDisplayName();
        }

    }

    public static class HipChatSendStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        @Inject
        private transient BuildUtils buildUtils;
        @Inject
        private transient HipChatSendStep step;
        @StepContextParameter
        private transient TaskListener listener;
        @StepContextParameter
        private transient Run<?, ?> run;

        @Override
        protected Void run() throws Exception {

            if (StringUtils.isBlank(step.message)) {
                //allow entire run to fail based on failOnError field
                if (step.failOnError) {
                    throw new AbortException(Messages.MessageRequiredError());
                } else {
                    listener.error(Messages.MessageRequiredError());
                }
                return null;
            }

            //default to global config values if not set in step, but allow step to override all global settings
            HipChatNotifier.DescriptorImpl hipChatDesc =
                    Jenkins.getInstance().getDescriptorByType(HipChatNotifier.DescriptorImpl.class);
            String token = step.token != null ? step.token : hipChatDesc.getToken();
            String room = step.room != null ? step.room : hipChatDesc.getRoom();
            String server = step.server != null ? step.server : hipChatDesc.getServer();
            String sendAs = step.sendAs != null ? step.sendAs : hipChatDesc.getSendAs();
            //default to gray if not set in step
            Color color = step.color != null ? step.color : Color.GRAY;
            boolean v2enabled = step.v2enabled != null ? step.v2enabled : hipChatDesc.isV2Enabled();

            HipChatService hipChatService = HipChatNotifier.getHipChatService(server, token, v2enabled, room, sendAs);

            logger.log(Level.FINER, "HipChat publish settings: api v2 - {0} server - {1} token - {2} room - {3}",
                    new Object[]{v2enabled, server, token, room});

            //attempt to publish message, log NotificationException, will allow run to continue
            try {
                String message = Util.replaceMacro(step.message,
                        buildUtils.collectParametersFor(Jenkins.getInstance(), run));
                hipChatService.publish(message, color.toString(), step.notify, step.textFormat);
                listener.getLogger().println(Messages.NotificationSuccessful(room));
            } catch (NotificationException ne) {
                listener.getLogger().println(Messages.NotificationFailed(ne.getMessage()));
                //allow entire run to fail based on failOnError field
                if (step.failOnError) {
                    throw new AbortException(Messages.NotificationFailed(ne.getMessage()));
                } else {
                    listener.error(Messages.NotificationFailed(ne.getMessage()));
                }
            }

            return null;
        }
    }
}