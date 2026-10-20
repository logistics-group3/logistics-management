import com.sun.jdi.Bootstrap;
import com.sun.jdi.Location;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.EventRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** 격리 JVM의 처리 스레드만 멈춘다. 서비스 메서드를 호출하거나 값을 수정하지 않는다. */
public class JdiGate {
    public static void main(String[] args) throws Exception {
        var connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(item -> item.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        var arguments = connector.defaultArguments();
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("port").setValue(args[0]);
        arguments.get("timeout").setValue("10000");
        VirtualMachine vm = connector.attach(arguments);
        Path output = Path.of(args[2]);
        int line = Integer.parseInt(args[1]);
        var types = vm.classesByName("com.sparta.logistics.order.application.OrderService");
        if (types.size() != 1) throw new IllegalStateException("OrderService 클래스 수: " + types.size());
        var locations = types.get(0).locationsOfLine(line);
        if (locations.size() != 1) throw new IllegalStateException("중단 위치 수: " + locations.size());
        Location location = locations.get(0);
        var manager = vm.eventRequestManager();
        var breakpoint = manager.createBreakpointRequest(location);
        breakpoint.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        breakpoint.addCountFilter(1);
        breakpoint.enable();
        // Note: 전역 MethodEntryRequest는 JVM 실행에 영향을 줄 수 있다. 두 메서드의 입구만 짧게 중단해 센다.
        for (String[] target : new String[][]{
                {"com.sparta.logistics.order.infrastructure.client.DeliveryClientAdapter", "create"},
                {"com.sparta.logistics.order.application.OrderWriter", "save"}}) {
            var type = vm.classesByName(target[0]).get(0);
            var entry = manager.createBreakpointRequest(type.methodsByName(target[1]).get(0).location());
            entry.putProperty("counter", target[1]);
            entry.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            entry.enable();
        }
        Files.writeString(output.resolve("armed.json"), "{\"line\":" + line
                + ",\"method\":\"" + location.method().name() + "\",\"suspendPolicy\":\"EVENT_THREAD\"}");
        System.out.println("ARMED " + location);
        int creates = 0;
        int saves = 0;
        try {
            while (true) {
                EventSet events = vm.eventQueue().remove(120000);
                if (events == null) throw new IllegalStateException("120초 안에 중단 지점에 도달하지 않았다");
                for (Event event : events) {
                    if (event instanceof BreakpointEvent hit) {
                        Object counter = hit.request().getProperty("counter");
                        if (counter != null) {
                            if (counter.equals("create")) creates++;
                            if (counter.equals("save")) saves++;
                            continue;
                        }
                        String json = "{\"at\":\"" + Instant.now() + "\",\"line\":" + line
                                + ",\"method\":\"" + location.method().name() + "\",\"thread\":\""
                                + hit.thread().name() + "\",\"deliveryCreateEntries\":" + creates
                                + ",\"orderSaveEntries\":" + saves + "}";
                        Files.writeString(output.resolve("hit.json"), json);
                        System.out.println("HIT " + json);
                        System.out.flush();
                        // 대조군은 stdin의 resume으로 재개한다. 장애 실험은 이 상태에서 외부가 JVM을 종료한다.
                        String action = new BufferedReader(new InputStreamReader(System.in)).readLine();
                        if ("resume".equals(action)) {
                            events.resume();
                            Files.writeString(output.resolve("released.json"), "{\"at\":\"" + Instant.now() + "\"}");
                            vm.dispose();
                            return;
                        }
                        return;
                    }
                }
                events.resume();
            }
        } catch (VMDisconnectedException expected) {
            System.out.println("VM_DISCONNECTED");
        }
    }
}
