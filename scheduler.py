import random
from collections import defaultdict
from typing import Dict, List, Tuple

DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
SHIFTS = ["morning", "afternoon", "evening"]

class Employee:
    def __init__(self, name: str, weekly_prefs: Dict[str, List[str] | str]):
        # normalize per-day preferences to ranked lists
        self.name = name
        self.weekly_prefs: Dict[str, List[str]] = {}
        for day in DAYS:
            pref = weekly_prefs.get(day, [])
            if isinstance(pref, str):
                self.weekly_prefs[day] = [pref]
            elif isinstance(pref, list):
                self.weekly_prefs[day] = list(pref)
            else:
                self.weekly_prefs[day] = []
        self.days_worked = 0
        self.assigned_days = set()

    def ranked_prefs(self, day: str) -> List[str]:
        return self.weekly_prefs.get(day, [])

def compute_target_per_shift(num_employees: int, max_days_per_week: int = 5) -> int:
    # how many people ideally fit per shift if load is spread evenly
    total_assignable = num_employees * max_days_per_week
    ideal = total_assignable // (len(DAYS) * len(SHIFTS))
    return max(2, ideal)

def schedule(
    employees: List[Employee],
    max_days_per_week: int = 5,
    rng_seed: int = 42
) -> Tuple[Dict[str, Dict[str, List[str]]], List[Tuple[str, str, int]], int]:
    rng = random.Random(rng_seed)
    n = len(employees)
    target_per_shift = compute_target_per_shift(n, max_days_per_week)

    sched: Dict[str, Dict[str, List[str]]] = {
        d: {s: [] for s in SHIFTS} for d in DAYS
    }
    conflict_queue: Dict[int, List[Employee]] = defaultdict(list)

    def can_assign(emp: Employee, day: str, shift: str) -> bool:
        if emp.days_worked >= max_days_per_week:
            return False
        if day in emp.assigned_days:
            return False
        if len(sched[day][shift]) >= target_per_shift:  # “full”
            return False
        return True

    # phase 1: honor preferences (with conflict deferral to next day)
    for d_idx, day in enumerate(DAYS):
        # previously deferred employees get first dibs
        deferred = conflict_queue.get(d_idx, [])
        rng.shuffle(deferred)
        for emp in deferred:
            placed = False
            # attempt a ranked preference that is open; otherwise any open shift
            open_shifts = [s for s in SHIFTS if can_assign(emp, day, s)]
            if open_shifts:
                ranked = [s for s in emp.ranked_prefs(day) if s in open_shifts] or open_shifts
                chosen = ranked[0]
                sched[day][chosen].append(emp.name)
                emp.days_worked += 1
                emp.assigned_days.add(day)
                placed = True
            if not placed and d_idx + 1 < len(DAYS):
                conflict_queue[d_idx + 1].append(emp)

        # everyone gets a chance based on daily ranking
        order = employees[:]
        rng.shuffle(order)
        for emp in order:
            if emp.days_worked >= max_days_per_week or day in emp.assigned_days:
                continue
            placed = False
            for choice in emp.ranked_prefs(day):
                if can_assign(emp, day, choice):
                    sched[day][choice].append(emp.name)
                    emp.days_worked += 1
                    emp.assigned_days.add(day)
                    placed = True
                    break
            if not placed:
                # try another shift same day
                for s in SHIFTS:
                    if can_assign(emp, day, s):
                        sched[day][s].append(emp.name)
                        emp.days_worked += 1
                        emp.assigned_days.add(day)
                        placed = True
                        break
                # still no? defer to next day
                if not placed and d_idx + 1 < len(DAYS):
                    conflict_queue[d_idx + 1].append(emp)

    # phase 2: guarantee minimum coverage of 2 per shift
    for day in DAYS:
        for shift in SHIFTS:
            while len(sched[day][shift]) < 2:
                candidates = [
                    e for e in employees
                    if e.days_worked < max_days_per_week and day not in e.assigned_days
                ]
                if not candidates:
                    break
                # fair fill: pick among those with fewest days worked; random tiebreak
                min_days = min(e.days_worked for e in candidates)
                pool = [e for e in candidates if e.days_worked == min_days]
                emp = rng.choice(pool)
                sched[day][shift].append(emp.name)
                emp.days_worked += 1
                emp.assigned_days.add(day)

    # phase 3: track any remaining under-filled shifts (infeasible staffing)
    unmet = []
    for day in DAYS:
        for shift in SHIFTS:
            if len(sched[day][shift]) < 2:
                unmet.append((day, shift, len(sched[day][shift])))

    return sched, unmet, target_per_shift

def print_schedule(sched: Dict[str, Dict[str, List[str]]]) -> None:
    header = f"{'Day':<5} | {'Morning':<28} | {'Afternoon':<28} | {'Evening':<28}"
    print(header)
    print("-" * len(header))
    for day in DAYS:
        m = ", ".join(sched[day]["morning"]) or "-"
        a = ", ".join(sched[day]["afternoon"]) or "-"
        e = ", ".join(sched[day]["evening"]) or "-"
        print(f"{day:<5} | {m:<28} | {a:<28} | {e:<28}")

if __name__ == "__main__":
    # sample input (supports both single choice and ranked lists)
    sample_employees = [
        Employee("Alice", {d: ["morning", "afternoon", "evening"] for d in DAYS}),
        Employee("Bob",   {d: ["morning", "evening", "afternoon"] for d in DAYS}),
        Employee("Carol", {d: ["afternoon", "morning", "evening"] for d in DAYS}),
        Employee("Dan",   {d: ["evening", "afternoon", "morning"] for d in DAYS}),
        Employee("Eva",   {"Mon": "morning", "Tue": "afternoon", "Wed": "evening", "Thu": "morning", "Fri": "afternoon"}),
        Employee("Frank", {"Mon": ["evening", "afternoon"], "Tue": ["morning"], "Wed": ["morning", "evening"], "Thu": ["afternoon"], "Fri": ["evening"]}),
        Employee("Grace", {d: ["afternoon", "evening", "morning"] for d in DAYS}),
        Employee("Hank",  {d: ["evening", "morning", "afternoon"] for d in DAYS}),
        Employee("Ivy",   {d: ["morning", "afternoon", "evening"] for d in DAYS}),
        Employee("Jack",  {d: ["afternoon", "evening", "morning"] for d in DAYS}),
    ]

    sched, unmet, target = schedule(sample_employees, max_days_per_week=5, rng_seed=42)
    print_schedule(sched)
    if unmet:
        print("\nUnmet coverage (infeasible staffing):")
        for day, shift, have in unmet:
            print(f"  {day} {shift}: {have}/2")
