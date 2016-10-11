package org.traccar;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Calendar;
import java.util.Date;
import org.junit.After;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Test;
import org.traccar.database.IdentityManager;
import org.traccar.model.Position;

public class FilterHandlerTest extends BaseTest {

    private FilterHandler filtingHandler;
    private FilterHandler passingHandler;

    @Before
    public void setUp() {
        passingHandler = new FilterHandler();
        filtingHandler = new FilterHandler();
        filtingHandler.setFilterInvalid(true);
        filtingHandler.setFilterZero(true);
        filtingHandler.setFilterDuplicate(true);
        filtingHandler.setFilterFuture(true);
        filtingHandler.setFilterApproximate(true);
        filtingHandler.setFilterStatic(true);
        filtingHandler.setFilterDistance(10);
        filtingHandler.setFilterLimit(10);
        filtingHandler.setFilterSpeed(10);
    }

    @After
    public void tearDown() {
        filtingHandler = null;
        passingHandler = null;
    }

    private Position createPosition(
            long deviceId,
            Date time,
            boolean valid,
            double latitude,
            double longitude,
            double altitude,
            double speed,
            double course) {

        Position p = new Position();
        p.setDeviceId(deviceId);
        p.setTime(time);
        p.setValid(valid);
        p.setLatitude(latitude);
        p.setLongitude(longitude);
        p.setAltitude(altitude);
        p.setSpeed(speed);
        p.setCourse(course);
        return p;
    }

    private Date createDate(int hour, int minute, int second) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.MONTH, c.get(Calendar.MONTH) - 1 < 0 ? Calendar.DECEMBER : c.get(Calendar.MONTH) - 1);
        c.set(Calendar.HOUR, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, second);
        return c.getTime();
    }

    @Test
    public void testFilterInvalid() throws Exception {

        Position position = createPosition(0, new Date(), true, 10, 10, 10, 10, 10);

        assertNotNull(filtingHandler.decode(null, null, position));
        assertNotNull(passingHandler.decode(null, null, position));

        position = createPosition(0, new Date(Long.MAX_VALUE), true, 10, 10, 10, 10, 10);

        assertNull(filtingHandler.decode(null, null, position));
        assertNotNull(passingHandler.decode(null, null, position));

        position = createPosition(0, new Date(), false, 10, 10, 10, 10, 10);

        assertNull(filtingHandler.decode(null, null, position));
        assertNotNull(passingHandler.decode(null, null, position));

        filtingHandler.setFilterLimit(0);
        filtingHandler.setFilterStatic(false);
        IdentityManager currentIdentityManager = Context.getIdentityManager();
        try {
            Context.init(mockLastPosition(currentIdentityManager, createPosition(0, createDate(14, 20, 45), true, 9.603557, -13.64191, 0d, 0d, 0d)));
            position = createPosition(0, createDate(14, 45, 45), true, 9.55564, -0.327507, 0d, 0d, 0d);
            assertNull(filtingHandler.decode(null, null, position));
            assertNotNull(passingHandler.decode(null, null, position));
        } finally {
            Context.init(currentIdentityManager);
        }
    }

    private IdentityManager mockLastPosition(final IdentityManager currentIdentityManager, final Position last) {
        return (IdentityManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IdentityManager.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("getLastPosition")) {
                            return last;
                        }
                        return method.invoke(currentIdentityManager, args);
                    }
                });
    }
}
