import com.sun.tools.attach.VirtualMachine;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

// Gracefully close ONLY the verification JVM identified by its process ID.
// The admin bean is enabled only on that temporary app, not on the normal demo.
public class ShutdownApp {
    public static void main(String[] args) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        String address;
        try {
            address = vm.startLocalManagementAgent();
        } finally {
            vm.detach();
        }
        var connector = JMXConnectorFactory.connect(new JMXServiceURL(address));
        try {
            System.out.println("SHUTDOWN_REQUESTED");
            System.out.flush();
            try {
                connector.getMBeanServerConnection().invoke(
                        new ObjectName("org.springframework.boot:type=Admin,name=SpringApplication"),
                        "shutdown", new Object[0], new String[0]);
                System.out.println("CONTEXT_CLOSED");
            } catch (java.io.IOException exception) {
                // The JVM can finish before RMI sends its reply. The Python caller
                // separately requires exit code 0, shutdown logs, and persisted rows.
                System.out.println("CONNECTION_CLOSED_DURING_SHUTDOWN");
            }
        } finally {
            try {
                connector.close();
            } catch (java.io.IOException exception) {
                // The target may already have exited after closing its context.
            }
        }
    }
}
