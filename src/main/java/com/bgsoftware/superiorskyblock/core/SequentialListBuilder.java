package com.bgsoftware.superiorskyblock.core;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.common.collections.Lists;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class SequentialListBuilder<E> {

    @Nullable
    private Comparator<? super E> comparator;
    @Nullable
    private Predicate<? super E> predicate;
    private boolean mutable = false;

    public SequentialListBuilder<E> sorted(@Nullable Comparator<? super E> comparator) {
        this.comparator = comparator;
        return this;
    }

    public SequentialListBuilder<E> filter(@Nullable Predicate<? super E> predicate) {
        this.predicate = predicate;
        return this;
    }

    public SequentialListBuilder<E> mutable() {
        this.mutable = true;
        return this;
    }

    public <O> List<E> build(Collection<O> collection, Function<O, E> mapper) {
        List<E> sequentialList = Lists.newLinkedList();

        collection.forEach(element -> {
            E mappedElement = mapper.apply(element);
            if (predicate == null || predicate.test(mappedElement))
                sequentialList.add(mappedElement);
        });

        return completeBuild(sequentialList);
    }


    public <O> List<O> map(Collection<E> collection, Function<E, O> mapper) {
        List<O> sequentialList = Lists.newLinkedList();

        collection.forEach(element -> {
            if (predicate == null || predicate.test(element))
                sequentialList.add(mapper.apply(element));
        });

        return completeBuild(sequentialList, null, this.mutable);
    }

    public <O> List<O> map(Iterator<E> iterator, Function<E, O> mapper) {
        List<O> sequentialList = Lists.newLinkedList();

        while (iterator.hasNext()) {
            E next = iterator.next();
            if (predicate == null || predicate.test(next))
                sequentialList.add(mapper.apply(next));
        }

        return completeBuild(sequentialList, null, this.mutable);
    }

    public List<E> build(Stream<E> stream) {
        List<E> sequentialList = Lists.newLinkedList();

        stream.forEach(element -> {
            if (predicate == null || predicate.test(element))
                sequentialList.add(element);
        });

        return completeBuild(sequentialList);
    }

    public List<E> build(Collection<E> collection) {
        List<E> sequentialList;

        if (predicate == null) {
            sequentialList = new LinkedList<>(collection);
        } else {
            sequentialList = Lists.newLinkedList();
            collection.forEach(element -> {
                if (predicate.test(element))
                    sequentialList.add(element);
            });
        }

        return completeBuild(sequentialList);
    }

    public List<E> build(Iterator<E> iterator) {
        List<E> sequentialList = Lists.newLinkedList();

        if (predicate == null) {
            while (iterator.hasNext())
                sequentialList.add(iterator.next());
        } else {
            while (iterator.hasNext()) {
                E next = iterator.next();
                if (predicate.test(next))
                    sequentialList.add(next);
            }
        }

        return completeBuild(sequentialList);
    }

    private List<E> completeBuild(List<E> sequentialList) {
        return completeBuild(sequentialList, this.comparator, this.mutable);
    }

    private static <E> List<E> completeBuild(List<E> sequentialList, @Nullable Comparator<? super E> comparator, boolean mutable) {
        if (sequentialList.isEmpty())
            return mutable ? sequentialList : Collections.emptyList();

        if (comparator != null)
            sequentialList.sort(comparator);

        return mutable ? sequentialList : Collections.unmodifiableList(sequentialList);
    }


}
