# Background synchronization with PowerSync on Android

The PowerSync SDK supports background synchronization (i.e., synchronization clients active without
a visible UI).
To use background synchronization, make sure that:

1. You only use a single instance of your `PowerSyncDatabase`. Using multiple instances means that
   multiple write connections are active, which can lead to "database is locked" issues, wasted
   resources due to multiple sync clients, and `watch()`ed queries not updating due to missing 
   update notifications.
2. You don't use separate processes for the UI and the background service.

These limitations are not inherent architectural issues, but sharing databases across processes is
not currently supported in the Android SDK. Please reach out if you need that feature!

PowerSync works by creating a long-lived connection to a synchronization service that pushes
database changes. This means that PowerSync works best with background services that can stay active
for longer periods of time.
At the same time, PowerSync is able to handle interruptions - so you can also connect your database
to the sync service on a short-lived task like e.g. WorkManager. PowerSync will try to download as
many operations as possible, and automatically picks up work from where it was previously stopped.

## Case study: Foreground services

The `androidBackgroundSync/` folder of the `supabase-todolist` demo contains a working example that
keeps synchronization active without a listening UI.
While it uses foreground services on Android, other APIs that run work in the same process would
work too.

To set up this type of sync in your app, follow these steps:

1. Set up your app for background sync. In the example, we declare a new service with
   `android:foregroundServiceType="dataSync"` and add the `FOREGROUND_SERVICE`,
   `FOREGROUND_SERVICE_DATA_SYNC` and `POST_NOTIFICATIONS` permissions.
2. Adapt an architecture that lets you share a PowerSync database between your compose UI and these
   background services. For Kotlin multiplatform support, our example uses Koin. If Android is your
   only target, you may want to consider Dagger/Hilt instead. As long as you ensure only a single
   instance of the PowerSync database is created, all is good!
3. Start the service at an appropriate time. In the `MainActivity` of the background sync example,
   we wait for the user to be logged in and then start the foreground service.
4. In the service, obtain an instance of the database in `onStartCommand` and call
   `database.connect` with your backend connector. If you want the synchronization to be tied to the
   service's lifecycle, call `database.disconnect()` when the service stops.

With those steps, PowerSync will keep synchronizing your database in the backend. You can try this
out in the example app by:

1. Starting it.
2. Closing the app by removing the activity from the recently used tasks.
3. Changing an entry in the database.
4. Activating airplane mode before opening the app again.
5. Despite no internet access while the app is open again, the row should be updated in the client
   too!

While this example uses foreground services (because it appears to be the only Android API that
allows us to keep long-running connections active), adopting a different pattern of e.g. using
Work Manager to schedule a single sync iteration regularly would also work with a similar pattern.
