package info.kgeorgiy.ja.bozhe.student;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import info.kgeorgiy.java.advanced.student.*;

@SuppressWarnings("unused")
public class StudentDB implements AdvancedQuery {

    /* -------------------------
              DB METHODS
       --------------------------*/

    @Override
    public List<Group> getGroupsByName(Collection<Student> students) {
        return getGroupBy(students, Comparators.STUDENT_BY_NAME);
    }

    @Override
    public List<Group> getGroupsById(Collection<Student> students) {
        return getGroupBy(students, Comparators.STUDENT_BY_ID);
    }

    @Override
    public GroupName getLargestGroup(Collection<Student> students) {
        return getLargestGroupBy(students, Comparators.largestGroup());
    }

    private Map<GroupName, Integer> getLargestGroupFirstName_countEntries(Collection<Student> students) {
        return getGroupsByName(students).stream()
                .collect(Collectors.toMap(Group::getName, group -> getDistinctFirstNames(group.getStudents()).size()));
    }

    @Override
    public GroupName getLargestGroupFirstName(Collection<Student> students) {
        return getLargestGroupBy(students,
                Comparators.largestGroupFirstName(getLargestGroupFirstName_countEntries(students)));
    }

    private Map<String, Integer> getMostPopularName_countEntries(Collection<Student> students) {
        return students.stream()
                .collect(Collectors.groupingBy(Student::getFirstName,
                        Collectors.mapping(Student::getGroup,
                                Collectors.collectingAndThen(Collectors.toSet(), Set::size))));
    }

    @Override
    public String getMostPopularName(Collection<Student> students) {
        return maxQuery(students, Comparators.mostPopularName(getMostPopularName_countEntries(students)), Student::getFirstName, "");
    }

    @Override
    public List<String> getFirstNames(List<Student> students) {
        return getQuery(students, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return getQuery(students, Student::getLastName);
    }

    @Override
    public List<GroupName> getGroups(List<Student> students) {
        return getQuery(students, Student::getGroup);
    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return getQuery(students, StudentDB::getFullName);
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return mapAndCollect(students, Student::getFirstName, Collectors.toCollection(TreeSet::new));
    }

    @Override
    public String getMaxStudentFirstName(List<Student> students) {
        return maxQuery(students, Comparators.STUDENT_BY_ID, Student::getFirstName, "");
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return sortQuery(students, Comparators.STUDENT_BY_ID);
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return sortQuery(students, Comparators.STUDENT_BY_NAME);
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String firstName) {
        return findQuery(students, (Student student) -> firstName.equals(student.getFirstName()));
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String lastName) {
        return findQuery(students, (Student student) -> lastName.equals(student.getLastName()));
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, GroupName groupName) {
        return findQuery(students, (Student student) -> groupName.equals(student.getGroup()));
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, GroupName groupName) {
        return students.stream()
                .filter((Student student) -> groupName.equals(student.getGroup()))
                .collect(Collectors.toMap(Student::getLastName, Student::getFirstName,
                        BinaryOperator.minBy(Comparator.naturalOrder())));
    }

    @Override
    public List<String> getFirstNames(Collection<Student> students, int[] indices) {
        return getByIndices(students, Student::getFirstName, indices);
    }

    @Override
    public List<String> getLastNames(Collection<Student> students, int[] indices) {
        return getByIndices(students, Student::getLastName, indices);
    }

    @Override
    public List<GroupName> getGroups(Collection<Student> students, int[] indices) {
        return getByIndices(students, Student::getGroup, indices);
    }

    @Override
    public List<String> getFullNames(Collection<Student> students, int[] indices) {
        return getByIndices(students, StudentDB::getFullName, indices);
    }

    /* -------------------------
               UTILITIES
       --------------------------*/

    private static String getFullName(Student student) {
        return student.getFirstName() + " " + student.getLastName();
    }

    private static <T, R> R mapAndCollect(Collection<Student> students, Function<Student, T> mapFunction, Collector<T, ?, R> collector) {
        return students.stream().map(mapFunction).collect(collector);
    }

    private static <T> T sortAndCollect(Collection<Student> students, Comparator<Student> comparator, Collector<? super Student, ?, T> collector) {
        return students.stream().sorted(comparator).collect(collector);
    }

    private List<Group> getGroupBy(Collection<Student> students, Comparator<Student> comparator) {
        return sortAndCollect(students, comparator, Collectors.groupingBy(Student::getGroup, Collectors.toList()))
                .entrySet()
                .stream()
                .map(pair -> new Group(pair.getKey(), pair.getValue()))
                .sorted(Comparators.GROUP_BY_NAME)
                .collect(Collectors.toList());

    }

    private GroupName getLargestGroupBy(Collection<Student> students, Comparator<Group> comparator) {
        return maxQuery(getGroupsByName(students), comparator, Group::getName, null);
    }

    private static <T> List<T> getByIndices(Collection<Student> students, Function<Student, T> field, int[] indices) {
        return Arrays.stream(indices)
                .mapToObj(List.copyOf(students)::get)
                .map(field)
                .collect(Collectors.toList());
    }

    private static <T> List<T> getQuery(List<Student> students, Function<Student, T> field) {
        return mapAndCollect(students, field, Collectors.toList());
    }

    private static List<Student> findQuery(Collection<Student> students, Predicate<Student> filter) {
        return students.stream().filter(filter).sorted(Comparators.STUDENT_BY_NAME).collect(Collectors.toList());
    }

    private static List<Student> sortQuery(Collection<Student> students, Comparator<Student> comparator) {
        return sortAndCollect(students, comparator, Collectors.toList());
    }

    private static <T, R> R maxQuery(Collection<T> collection, Comparator<T> comparator, Function<T, R> mapFunction, R defaultValue) {
        return collection.stream()
                .max(comparator)
                .map(mapFunction)
                .orElse(defaultValue);
    }

    private static class Comparators {
        private final static Comparator<Student> STUDENT_BY_NAME =
                Comparator.comparing(Student::getLastName)
                        .thenComparing(Student::getFirstName)
                        .reversed()
                        .thenComparingInt(Student::getId);

        private final static Comparator<Student> STUDENT_BY_ID =
                Comparator.comparingInt(Student::getId);

        private final static Comparator<Group> GROUP_BY_NAME =
                Comparator.comparing(Group::getName);

        private static Comparator<Group> largestGroup() {
            return Comparator.comparingInt((Group group) -> group.getStudents().size())
                    .thenComparing(GROUP_BY_NAME);
        }

        private static Comparator<Group> largestGroupFirstName(Map<GroupName, Integer> counts) {
            return Comparator.comparingLong((Group group) -> counts.get(group.getName()))
                    .thenComparing(GROUP_BY_NAME.reversed());
        }

        private static Comparator<Student> mostPopularName(Map<String, Integer> counts) {
            return Comparator.comparing((Student student) -> counts.get(student.getFirstName()))
                    .thenComparing(Student::getFirstName);
        }
    }
}

// java -cp . -p . -m info.kgeorgiy.java.advanced.student GroupQuery info.kgeorgiy.ja.bozhe.student.StudentDB