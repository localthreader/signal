package net.threader.signal;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import net.threader.signal.util.Pair;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class EventProcessor {
    private Multimap<Class<?>, Pair<EventListener, Method>> registeredHandlers = ArrayListMultimap.create();
    private Map<Class<?>, Queue<Pair<EventListener, Method>>> handleQueues = new HashMap<>();

    public void register(EventListener listener) {
        Set<Class<?>> affectedEvents = new HashSet<>();
        Arrays.stream(listener.getClass().getDeclaredMethods())
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getParameterTypes()[0].getSuperclass().equals(Event.class))
                .filter(method -> method.getAnnotation(Handler.class) != null)
                .forEach(method -> {
                    registeredHandlers.put(method.getParameterTypes()[0], new Pair<>(listener, method));
                    affectedEvents.add(method.getParameterTypes()[0]);
                });

        affectedEvents.forEach(x -> {
            List<Pair<EventListener, Method>> list = new ArrayList<>(new HashSet<>(registeredHandlers.get(x)));
            list.sort(Comparator.comparingInt(p -> p.getSecond().getAnnotation(Handler.class).priority()));
            Collections.reverse(list);
            handleQueues.remove(x);
            handleQueues.put(x, new LinkedBlockingQueue<>(list));
        });
    }

    public <E extends Event> void post(E event) {
        Optional.ofNullable(handleQueues.get(event.getClass())).ifPresent(queue -> {
            Queue<Pair<EventListener, Method>> copy = new LinkedBlockingQueue<>(queue);
            while(!copy.isEmpty()) {
                Pair<EventListener, Method> pair = copy.poll();
                try {
                    if(event instanceof ICancellable && ((ICancellable) event).isCancelled()) {
                        break;
                    }
                    pair.getSecond().invoke(pair.getFirst(), event);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
