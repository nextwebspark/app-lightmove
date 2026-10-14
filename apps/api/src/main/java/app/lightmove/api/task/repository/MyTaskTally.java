package app.lightmove.api.task.repository;

/** How many of one person's open tasks fall on each tab of My tasks they keep themselves. */
public interface MyTaskTally {

    long getOverdue();

    long getToday();

    long getUpcoming();
}
