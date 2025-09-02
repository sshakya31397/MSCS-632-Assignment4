import java.util.*;

public class Main {

    static final List<String> DAYS = Arrays.asList("Mon","Tue","Wed","Thu","Fri","Sat","Sun");
    static final List<String> SHIFTS = Arrays.asList("morning","afternoon","evening");

    static class Employee {
        String name;
        Map<String, List<String>> weeklyPrefs = new HashMap<>();
        int daysWorked = 0;
        Set<String> assignedDays = new HashSet<>();

        Employee(String name, Map<String, Object> prefs) {
            this.name = name;
            for (String day : DAYS) {
                Object v = prefs.getOrDefault(day, new ArrayList<String>());
                if (v instanceof String) {
                    weeklyPrefs.put(day, new ArrayList<>(Arrays.asList((String) v)));
                } else if (v instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> lst = (List<String>) v;
                    weeklyPrefs.put(day, new ArrayList<>(lst));
                } else {
                    weeklyPrefs.put(day, new ArrayList<>());
                }
            }
        }

        List<String> rankedPrefs(String day) {
            return weeklyPrefs.getOrDefault(day, Collections.emptyList());
        }
    }

    static int computeTargetPerShift(int numEmployees, int maxDaysPerWeek) {
        int totalAssignable = numEmployees * maxDaysPerWeek;
        int ideal = totalAssignable / (DAYS.size() * SHIFTS.size());
        return Math.max(2, ideal);
    }

    static class Result {
        Map<String, Map<String, List<String>>> sched;
        List<String> unmet;
        int target;

        Result(Map<String, Map<String, List<String>>> s, List<String> u, int t) {
            sched = s; unmet = u; target = t;
        }
    }

    static Result schedule(List<Employee> employees, int maxDaysPerWeek, long seed) {
        Random rng = new Random(seed);
        int n = employees.size();
        int target = computeTargetPerShift(n, maxDaysPerWeek);

        Map<String, Map<String, List<String>>> sched = new LinkedHashMap<>();
        for (String d : DAYS) {
            Map<String, List<String>> perShift = new LinkedHashMap<>();
            for (String s : SHIFTS) perShift.put(s, new ArrayList<>());
            sched.put(d, perShift);
        }

        Map<Integer, List<Employee>> conflictQueue = new HashMap<>();

        java.util.function.BiFunction<Employee, String, Boolean> canAssignAny = (emp, day) -> {
            if (emp.daysWorked >= maxDaysPerWeek) return false;
            return !emp.assignedDays.contains(day);
        };

        java.util.function.BiFunction<Employee, String, Boolean> canAssignPref = (emp, shift) -> true;

        // helper
        java.util.function.Predicate<Employee> notMaxed = e -> e.daysWorked < maxDaysPerWeek;

        // inner helper
        class CanAssign {
            boolean test(Employee emp, String day, String shift) {
                if (emp.daysWorked >= maxDaysPerWeek) return false;
                if (emp.assignedDays.contains(day)) return false;
                return sched.get(day).get(shift).size() < target;
            }
        }
        CanAssign canAssign = new CanAssign();

        // phase 1: preferences + conflict deferral
        for (int dIdx = 0; dIdx < DAYS.size(); dIdx++) {
            String day = DAYS.get(dIdx);

            // handle deferred first
            List<Employee> deferred = conflictQueue.getOrDefault(dIdx, new ArrayList<>());
            Collections.shuffle(deferred, rng);
            for (Employee emp : deferred) {
                boolean placed = false;
                List<String> open = new ArrayList<>();
                for (String s : SHIFTS) if (canAssign.test(emp, day, s)) open.add(s);
                if (!open.isEmpty()) {
                    List<String> ranked = new ArrayList<>();
                    for (String r : emp.rankedPrefs(day)) if (open.contains(r)) ranked.add(r);
                    if (ranked.isEmpty()) ranked = open;
                    String chosen = ranked.get(0);
                    sched.get(day).get(chosen).add(emp.name);
                    emp.daysWorked++;
                    emp.assignedDays.add(day);
                    placed = true;
                }
                if (!placed && dIdx + 1 < DAYS.size()) {
                    conflictQueue.computeIfAbsent(dIdx + 1, k -> new ArrayList<>()).add(emp);
                }
            }

            // everyone else
            List<Employee> order = new ArrayList<>(employees);
            Collections.shuffle(order, rng);
            for (Employee emp : order) {
                if (emp.daysWorked >= maxDaysPerWeek || emp.assignedDays.contains(day)) continue;
                boolean placed = false;
                for (String pref : emp.rankedPrefs(day)) {
                    if (canAssign.test(emp, day, pref)) {
                        sched.get(day).get(pref).add(emp.name);
                        emp.daysWorked++;
                        emp.assignedDays.add(day);
                        placed = true;
                        break;
                    }
                }
                if (!placed) {
                    for (String s : SHIFTS) {
                        if (canAssign.test(emp, day, s)) {
                            sched.get(day).get(s).add(emp.name);
                            emp.daysWorked++;
                            emp.assignedDays.add(day);
                            placed = true;
                            break;
                        }
                    }
                    if (!placed && dIdx + 1 < DAYS.size()) {
                        conflictQueue.computeIfAbsent(dIdx + 1, k -> new ArrayList<>()).add(emp);
                    }
                }
            }
        }

        // phase 2: ensure minimum coverage
        for (String day : DAYS) {
            for (String shift : SHIFTS) {
                while (sched.get(day).get(shift).size() < 2) {
                    List<Employee> candidates = new ArrayList<>();
                    for (Employee e : employees) {
                        if (e.daysWorked < maxDaysPerWeek && !e.assignedDays.contains(day)) {
                            candidates.add(e);
                        }
                    }
                    if (candidates.isEmpty()) break;
                    int minDays = candidates.stream().mapToInt(e -> e.daysWorked).min().orElse(0);
                    List<Employee> pool = new ArrayList<>();
                    for (Employee e : candidates) if (e.daysWorked == minDays) pool.add(e);
                    Employee chosen = pool.get(rng.nextInt(pool.size()));
                    sched.get(day).get(shift).add(chosen.name);
                    chosen.daysWorked++;
                    chosen.assignedDays.add(day);
                }
            }
        }

        // unmet coverage report
        List<String> unmet = new ArrayList<>();
        for (String day : DAYS) {
            for (String shift : SHIFTS) {
                int have = sched.get(day).get(shift).size();
                if (have < 2) unmet.add(day + " " + shift + ": " + have + "/2");
            }
        }
        return new Result(sched, unmet, target);
    }

    static void printSchedule(Map<String, Map<String, List<String>>> sched) {
        String header = String.format("%-5s | %-28s | %-28s | %-28s",
                "Day","Morning","Afternoon","Evening");
        System.out.println(header);
        System.out.println("-".repeat(header.length()));
        for (String day : DAYS) {
            String m = String.join(", ", sched.get(day).get("morning"));
            String a = String.join(", ", sched.get(day).get("afternoon"));
            String e = String.join(", ", sched.get(day).get("evening"));
            if (m.isEmpty()) m = "-";
            if (a.isEmpty()) a = "-";
            if (e.isEmpty()) e = "-";
            System.out.println(String.format("%-5s | %-28s | %-28s | %-28s", day, m, a, e));
        }
    }

    public static void main(String[] args) {
        // sample dataset: supports single and ranked preferences
        List<Employee> emps = new ArrayList<>();
        Map<String, Object> everyMorning = new HashMap<>();
        for (String d: DAYS) everyMorning.put(d, Arrays.asList("morning","afternoon","evening"));
        Map<String, Object> mornEven = new HashMap<>();
        for (String d: DAYS) mornEven.put(d, Arrays.asList("morning","evening","afternoon"));
        Map<String, Object> aftMorn = new HashMap<>();
        for (String d: DAYS) aftMorn.put(d, Arrays.asList("afternoon","morning","evening"));
        Map<String, Object> eveAft = new HashMap<>();
        for (String d: DAYS) eveAft.put(d, Arrays.asList("evening","afternoon","morning"));

        Map<String, Object> eva = new HashMap<>();
        eva.put("Mon","morning"); eva.put("Tue","afternoon"); eva.put("Wed","evening");
        eva.put("Thu","morning"); eva.put("Fri","afternoon");

        Map<String, Object> frank = new HashMap<>();
        frank.put("Mon", Arrays.asList("evening","afternoon"));
        frank.put("Tue", Arrays.asList("morning"));
        frank.put("Wed", Arrays.asList("morning","evening"));
        frank.put("Thu", Arrays.asList("afternoon"));
        frank.put("Fri", Arrays.asList("evening"));

        Map<String, Object> aftEvenMorn = new HashMap<>();
        for (String d: DAYS) aftEvenMorn.put(d, Arrays.asList("afternoon","evening","morning"));
        Map<String, Object> evenMornAft = new HashMap<>();
        for (String d: DAYS) evenMornAft.put(d, Arrays.asList("evening","morning","afternoon"));

        emps.add(new Employee("Alice", everyMorning));
        emps.add(new Employee("Bob",   mornEven));
        emps.add(new Employee("Carol", aftMorn));
        emps.add(new Employee("Dan",   eveAft));
        emps.add(new Employee("Eva",   eva));
        emps.add(new Employee("Frank", frank));
        emps.add(new Employee("Grace", aftEvenMorn));
        emps.add(new Employee("Hank",  evenMornAft));
        emps.add(new Employee("Ivy",   everyMorning));
        emps.add(new Employee("Jack",  aftEvenMorn));

        Result r = schedule(emps, 5, 42L);
        printSchedule(r.sched);
        if (!r.unmet.isEmpty()) {
            System.out.println("\nUnmet coverage (infeasible staffing):");
            for (String line : r.unmet) System.out.println("  " + line);
        }
    }
}
