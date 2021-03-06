package com.github.rahulsom.grooves.queries;

import com.github.rahulsom.grooves.api.AggregateType;
import com.github.rahulsom.grooves.api.events.BaseEvent;
import com.github.rahulsom.grooves.api.events.RevertEvent;
import com.github.rahulsom.grooves.api.snapshots.VersionedSnapshot;
import com.github.rahulsom.grooves.queries.internal.*;
import rx.Observable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.rahulsom.grooves.queries.internal.Utils.returnOrRedirect;
import static com.github.rahulsom.grooves.queries.internal.Utils.stringifyEvents;
import static rx.Observable.empty;

/**
 * Default interface to help in building versioned snapshots.
 *
 * @param <AggregateT>  The aggregate over which the query executes
 * @param <EventIdT>    The type of the Event's id field
 * @param <EventT>      The type of the Event
 * @param <SnapshotIdT> The type of the Snapshot's id field
 * @param <SnapshotT>   The type of the Snapshot
 *
 * @author Rahul Somasunderam
 */
public interface VersionedQuerySupport<
        AggregateIdT,
        AggregateT extends AggregateType<AggregateIdT>,
        EventIdT,
        EventT extends BaseEvent<AggregateIdT, AggregateT, EventIdT, EventT>,
        SnapshotIdT,
        SnapshotT extends VersionedSnapshot<AggregateIdT, AggregateT, SnapshotIdT, EventIdT,
                EventT>,
        QueryT extends BaseQuery<AggregateIdT, AggregateT, EventIdT, EventT, SnapshotIdT, SnapshotT,
                QueryT>
        >
        extends
        BaseQuery<AggregateIdT, AggregateT, EventIdT, EventT, SnapshotIdT, SnapshotT, QueryT> {

    /**
     * Finds the last usable snapshot. For a given maxPosition, finds a snapshot that's older than
     * that version number so a new one can be incrementally computed if possible.
     *
     * @param aggregate   The aggregate for which a snapshot is to be computed
     * @param maxPosition The maximum allowed version of the snapshot that is deemed usable
     *
     * @return An Observable that returns at most one snapshot
     */
    default Observable<SnapshotT> getLastUsableSnapshot(
            final AggregateT aggregate, long maxPosition) {
        return getSnapshot(maxPosition, aggregate)
                .defaultIfEmpty(createEmptySnapshot())
                .doOnNext(it -> {
                    final String snapshotAsString =
                            it.getLastEventPosition() == null ? "<none>" :
                                    it.getLastEventPosition() == 0 ? "<none>" :
                                            it.toString();
                    getLog().debug("  -> Last Usable Snapshot: {}", snapshotAsString);
                    it.setAggregate(aggregate);
                });
    }

    /**
     * Given a last event, finds the latest snapshot older than that event, and events between the
     * snapshot and the desired version.
     *
     * @param aggregate The aggregate for which such data is desired
     * @param version   The version of the snapshot that is desired
     *
     * @return A Tuple containing the snapshot and the events
     */
    default Observable<Pair<SnapshotT, List<EventT>>> getSnapshotAndEventsSince(
            AggregateT aggregate, long version) {
        return getSnapshotAndEventsSince(aggregate, version, true);
    }

    /**
     * Given a last event, finds the latest snapshot older than that event, and events between the
     * snapshot and the desired version.
     *
     * @param aggregate            The aggregate for which such data is desired
     * @param version              The version of the snapshot that is desired
     * @param reuseEarlierSnapshot Whether earlier snapshots can be reused for this computation. It
     *                             is generally a good idea to set this to true unless there are
     *                             known reverts that demand this be set to false.
     *
     * @return A Tuple containing the snapshot and the events
     */
    default Observable<Pair<SnapshotT, List<EventT>>> getSnapshotAndEventsSince(
            AggregateT aggregate, long version, boolean reuseEarlierSnapshot) {
        if (reuseEarlierSnapshot) {
            return getLastUsableSnapshot(aggregate, version).flatMap(lastSnapshot -> {
                final Observable<EventT> uncomputedEvents =
                        getUncomputedEvents(aggregate, lastSnapshot, version);

                return uncomputedEvents.toList()
                        .flatMap(events -> {
                            if (events.stream().anyMatch(it -> it instanceof RevertEvent)) {
                                List<EventT> reverts = events.stream()
                                        .filter(it -> it instanceof RevertEvent).collect(
                                                Collectors.toList());
                                getLog().info("     Uncomputed reverts exist: {}",
                                        stringifyEvents(reverts));
                                return getSnapshotAndEventsSince(
                                        aggregate, version, false);
                            } else {
                                getLog().debug("     Events since last snapshot: {}",
                                        stringifyEvents(events));
                                return Observable.just(new Pair<>(lastSnapshot, events));

                            }
                        });

            });

        } else {
            SnapshotT lastSnapshot = createEmptySnapshot();

            final Observable<List<EventT>> uncomputedEvents =
                    getUncomputedEvents(aggregate, lastSnapshot, version)
                            .toList();

            return uncomputedEvents
                    .doOnNext(ue -> getLog().debug("     Events since origin: {}",
                            stringifyEvents(ue)))
                    .map(ue -> new Pair<>(lastSnapshot, ue));
        }

    }

    default Executor<AggregateIdT, AggregateT, EventIdT, EventT, SnapshotIdT, SnapshotT, QueryT
            > getExecutor() {
        return new QueryExecutor<>();
    }

    Observable<EventT> getUncomputedEvents(
            AggregateT aggregate, SnapshotT lastSnapshot, long version);


    /**
     * Computes a snapshot for specified version of an aggregate.
     *
     * @param aggregate The aggregate
     * @param version   The version number, starting at 1
     *
     * @return An Observable that returns at most one Snapshot
     */
    default Observable<SnapshotT> computeSnapshot(AggregateT aggregate, long version) {
        return computeSnapshot(aggregate, version, true);
    }

    /**
     * Computes a snapshot for specified version of an aggregate.
     *
     * @param aggregate The aggregate
     * @param version   The version number, starting at 1
     * @param redirect  If there has been a deprecation, redirect to the current aggregate's
     *                  snapshot. Defaults to true.
     *
     * @return An Observable that returns at most one Snapshot
     */
    default Observable<SnapshotT> computeSnapshot(
            AggregateT aggregate, long version, boolean redirect) {

        getLog().info("Computing snapshot for {} version {}",
                aggregate, version == Long.MAX_VALUE ? "<LATEST>" : version);

        return getSnapshotAndEventsSince(aggregate, version).flatMap(seTuple2 -> {
            List<EventT> events = seTuple2.getSecond();
            SnapshotT lastUsableSnapshot = seTuple2.getFirst();

            getLog().info("Events: {}", events);

            if (events.stream().anyMatch(it -> it instanceof RevertEvent)) {
                return lastUsableSnapshot
                        .getAggregateObservable()
                        .flatMap(aggregate1 -> aggregate1 == null ?
                                computeSnapshotAndEvents(
                                        aggregate, version, redirect, events, lastUsableSnapshot) :
                                empty())
                        .map(Observable::just)
                        .defaultIfEmpty(computeSnapshotAndEvents(
                                aggregate, version, redirect, events, lastUsableSnapshot))
                        .flatMap(it -> it);
            }
            return computeSnapshotAndEvents(
                    aggregate, version, redirect, events, lastUsableSnapshot);
        });

    }

    /**
     * Computes snapshot and events based on the last usable snapshot.
     *
     * @param aggregate          The aggregate on which we are working
     * @param version            The version that we desire
     * @param redirect           Whether a redirect should be performed if the aggregate has been
     *                           deprecated by another aggregate
     * @param events             The list of events
     * @param lastUsableSnapshot The last known usable snapshot
     *
     * @return An observable of the snapshot
     */
    default Observable<SnapshotT> computeSnapshotAndEvents(
            AggregateT aggregate, long version, boolean redirect, List<EventT> events,
            SnapshotT lastUsableSnapshot) {
        lastUsableSnapshot.setAggregate(aggregate);

        Observable<EventT> forwardOnlyEvents = Utils.getForwardOnlyEvents(
                events, getExecutor(), () -> getSnapshotAndEventsSince(aggregate, version, false)
        );

        final Observable<SnapshotT> snapshotObservable =
                getExecutor().applyEvents((QueryT) this, lastUsableSnapshot, forwardOnlyEvents,
                        new ArrayList<>(), Collections.singletonList(aggregate), aggregate);
        return snapshotObservable
                .doOnNext(snapshot -> {
                    if (!events.isEmpty()) {
                        snapshot.setLastEvent(events.get(events.size() - 1));
                    }

                    getLog().info("  --> Computed: {}", snapshot);
                })
                .flatMap(it -> returnOrRedirect(redirect, events, it,
                        () -> it.getDeprecatedByObservable()
                                .flatMap(x -> computeSnapshot(x, version))
                ));
    }

}
