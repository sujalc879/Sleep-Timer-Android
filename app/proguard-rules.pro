# Room 2.2.5 constructs this Glance/WorkManager database with Class.newInstance().
# Its consumer rule retains the class but not the constructor under R8 full mode.
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}
