package com.kenyarealestate.notification.channel;

import com.kenyarealestate.notification.entity.Channel;
import com.kenyarealestate.notification.entity.Notification;

public interface DeliveryChannel {

    Channel type();

    void deliver(Notification notification) throws DeliveryException;
}
