/**
 * Notification domain package.
 *
 * <ul>
 *   <li>{@link com.servicedesk.domain.notification.NotificationDispatcher} - an event listener
 *       (<b>Observer</b>) that decides who to tell and delivers the messages.
 *   <li>{@link com.servicedesk.domain.notification.NotificationPolicy} - who hears about what, on
 *       which channels (<b>Strategy</b>).
 *   <li>{@link com.servicedesk.domain.notification.NotificationFactory} - writes the message text
 *       for each recipient (<b>Factory</b>).
 *   <li>{@link com.servicedesk.domain.notification.NotificationChannel} - in-app, e-mail and SMS;
 *       e-mail and SMS go through the {@link com.servicedesk.domain.notification.EmailSender} and
 *       {@link com.servicedesk.domain.notification.SmsSender} ports, implemented by <b>Adapters</b>
 *       in the infrastructure layer.
 * </ul>
 */
package com.servicedesk.domain.notification;
