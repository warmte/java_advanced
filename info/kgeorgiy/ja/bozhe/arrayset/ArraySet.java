package info.kgeorgiy.ja.bozhe.arrayset;

import java.util.*;

@SuppressWarnings("unused")
public class ArraySet<T> extends AbstractSet<T> implements NavigableSet<T> {
    private final DescendibleList<T> data;
    private final Comparator<? super T> comparator;

    public ArraySet() {
        this(Collections.emptyList(), null);
    }

    public ArraySet(Collection<? extends T> collection) {
        this(collection, null);
    }

    public ArraySet(Comparator<? super T> cmp) {
        this(Collections.emptyList(), cmp);
    }

    public ArraySet(Collection<? extends T> collection, Comparator<? super T> cmp) {
        TreeSet<T> treeSet = new TreeSet<>(cmp);
        treeSet.addAll(collection);
        this.data = new DescendibleList<>(new ArrayList<>(treeSet), false);
        this.comparator = cmp;
    }

    public ArraySet(SortedSet<T> collection) {
        this.data = new DescendibleList<>(new ArrayList<>(collection), false);
        this.comparator = collection.comparator();
    }

    private ArraySet(List<T> data, Comparator<? super T> cmp) {
        if (data instanceof DescendibleList) {
            DescendibleList<T> descendibleData = (DescendibleList<T>)data;
            this.data = new DescendibleList<>(descendibleData.data, descendibleData.reversed);
        } else {
            this.data = new DescendibleList<>(data, false);
        }
        this.comparator = cmp;
    }

    private T safeGet(int ind) {
        return 0 <= ind && ind < size() ? data.get(ind) : null;
    }

    private int getIndex(T el, BorderInclusion borderInclusion, BorderType borderType) {
        int index = Collections.binarySearch(data, el, comparator);
        if (index >= 0) {
            if (borderInclusion == BorderInclusion.INCLUSIVE) {
                return index;
            } else {
                return borderType == BorderType.LOWER ? index - 1 : index + 1;
            }
        } else {
            return -index - 1 + (borderType == BorderType.LOWER ? -1 : 0);
        }
    }

    @Override
    public T lower(T el) {
        return safeGet(getIndex(el, BorderInclusion.NOT_INCLUSIVE, BorderType.LOWER));
    }

    @Override
    public T floor(T el) {
        return safeGet(getIndex(el, BorderInclusion.INCLUSIVE, BorderType.LOWER));
    }

    @Override
    public T ceiling(T el) {
        return safeGet(getIndex(el, BorderInclusion.INCLUSIVE, BorderType.UPPER));
    }

    @Override
    public T higher(T el) {
        return safeGet(getIndex(el, BorderInclusion.NOT_INCLUSIVE, BorderType.UPPER));
    }

    @Override
    public T pollFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T pollLast() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return data.size();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(Object el) {
        return Collections.binarySearch(data, (T) el, comparator) >= 0;
    }

    @Override
    public Iterator<T> iterator() {
        return data.iterator();
    }

    // NOTE: don't needlessly override

    @Override
    public NavigableSet<T> descendingSet() {
        return new ArraySet<>(new DescendibleList<>(data), Collections.reverseOrder(comparator));
    }

    @Override
    public Iterator<T> descendingIterator() {
        return descendingSet().iterator();
    }

    private NavigableSet<T> subSetByIndex(int leftIndex, int rightIndex) {
        return rightIndex >= leftIndex
                ? new ArraySet<>(data.subList(leftIndex, rightIndex + 1), comparator)
                : new ArraySet<>(comparator);
    }

    @SuppressWarnings("unchecked")
    @Override
    public NavigableSet<T> subSet(T left, boolean leftInclusive, T right, boolean rightInclusive) {
        if ((comparator != null ? comparator.compare(left, right) : ((Comparable<T>) left).compareTo(right)) > 0) {
            throw new IllegalArgumentException("Start element greater than end element.");
        }
        return subSetByIndex(getIndex(left, getInclusion(leftInclusive), BorderType.UPPER),
                getIndex(right, getInclusion(rightInclusive), BorderType.LOWER));
    }

    @Override
    public NavigableSet<T> headSet(T right, boolean rightInclusive) {
        return subSetByIndex(0, getIndex(right, getInclusion(rightInclusive), BorderType.LOWER));
    }

    @Override
    public NavigableSet<T> tailSet(T left, boolean leftInclusive) {
        return subSetByIndex(getIndex(left, getInclusion(leftInclusive), BorderType.UPPER), size() - 1);
    }

    @Override
    public SortedSet<T> subSet(T left, T right) {
        return subSet(left, true, right, false);
    }

    @Override
    public SortedSet<T> headSet(T right) {
        return headSet(right, false);
    }

    @Override
    public SortedSet<T> tailSet(T left) {
        return tailSet(left, true);
    }

    @Override
    public Comparator<? super T> comparator() {
        return this.comparator;
    }

    @Override
    public T first() {
        Validate.nonEmpty(isEmpty());
        return safeGet(0);
    }

    @Override
    public T last() {
        Validate.nonEmpty(isEmpty());
        return safeGet(size() - 1);
    }

    private static class DescendibleList<T> extends AbstractList<T> implements RandomAccess {
        private final List<T> data;
        private final boolean reversed;

        DescendibleList(DescendibleList<T> data) {
            this.data = data.data;
            this.reversed = !data.reversed;
        }

        DescendibleList(List<T> data, boolean reversed) {
            this.data = data;
            this.reversed = reversed;
        }

        @Override
        public T get(int ind) {
            return data.get(reversed ? size() - ind - 1 : ind);
        }

        @Override
        public int size() {
            return data.size();
        }
    }

    private static class Validate {
        public static void nonEmpty(boolean isEmpty) {
            if (isEmpty) {
                throw new NoSuchElementException("ArraySet is empty.");
            }
        }
    }

    private enum BorderInclusion {
        INCLUSIVE,
        NOT_INCLUSIVE
    }

    private BorderInclusion getInclusion(boolean flag) {
        return flag ? BorderInclusion.INCLUSIVE : BorderInclusion.NOT_INCLUSIVE;
    }

    private enum BorderType {
        LOWER,
        UPPER
    }
}

// java -cp . -p . -m info.kgeorgiy.java.advanced.arrayset AdvancedSet info.kgeorgiy.ja.bozhe.arrayset.ArraySet