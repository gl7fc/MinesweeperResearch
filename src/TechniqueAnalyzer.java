import java.util.*;

public class TechniqueAnalyzer {

    private static final int MINE = -1;
    private static final int IGNORE = -2;
    private static final int FLAGGED = -3;
    private static final int SAFE = -4;

    public static final int LV_UNSOLVED = -1;
    public static final int LV_1 = 1;
    public static final int LV_2 = 2;
    public static final int LV_3 = 3;
    public static final int LV_4 = 4;
    public static final int LV_5 = 5;
    public static final int LV_6 = 6;

    private int[] board;
    private final int[] completeBoard;
    private final int[] difficultyMap;
    private final int size;

    private Map<Set<Integer>, Region> regionPool;
    private int regionIdCounter = 0;
    private boolean isDerivedRegionsGenerated = false;

    private AnalysisLogger logger;
    private int currentRound = 0;

    private Set<Integer> lastConfirmedCells = new HashSet<>();
    private Map<Integer, Integer> cellDepthMap = new HashMap<>();

    public TechniqueAnalyzer(int[] currentBoard, int[] solution, int size) {
        this.board = Arrays.copyOf(currentBoard, currentBoard.length);
        this.completeBoard = solution;
        this.size = size;
        this.difficultyMap = new int[currentBoard.length];
        Arrays.fill(this.difficultyMap, LV_UNSOLVED);
        this.regionPool = new HashMap<>();
        this.logger = new AnalysisLogger();
    }

    private static class DeductionResult {
        Map<Integer, Integer> deduced;
        Map<Integer, Region> cellToSourceRegion;
        int level;

        DeductionResult() {
            this.deduced = new HashMap<>();
            this.cellToSourceRegion = new HashMap<>();
            this.level = 0;
        }

        boolean isEmpty() {
            return deduced.isEmpty();
        }
    }

    public void analyze() {
        for (int i = 0; i < board.length; i++) {
            if (board[i] >= 0) {
                difficultyMap[i] = 0;
                cellDepthMap.put(i, 0);
                logger.logInitialHint(i, board[i]);
            }
        }

        boolean changed = true;
        currentRound = 1;
        lastConfirmedCells = Collections.emptySet();

        while (changed) {
            changed = false;
            logger.startNewRound();

            regionPool = updateAndGenerateRegions(board, regionPool, !isDerivedRegionsGenerated, lastConfirmedCells);
            if (!isDerivedRegionsGenerated) {
                isDerivedRegionsGenerated = true;
            }

            DeductionResult result = solveFromPool(board, regionPool);

            if (!result.isEmpty()) {
                logDeduction(result);

                for (int cellIdx : result.deduced.keySet()) {
                    if (difficultyMap[cellIdx] == LV_UNSOLVED) {
                        Region sourceRegion = result.cellToSourceRegion.get(cellIdx);
                        int level = Math.max(LV_1, sourceRegion.getOriginLevel());
                        difficultyMap[cellIdx] = level;
                    }
                }

                lastConfirmedCells = result.deduced.keySet();
                applyResult(result.deduced);
                changed = true;
                currentRound++;
            } else {
                int[] lv4Result = solveLv4();

                if (lv4Result != null) {
                    int cellIdx = lv4Result[0];
                    int value = lv4Result[1];
                    int level = lv4Result[2];

                    board[cellIdx] = value;

                    if (difficultyMap[cellIdx] == LV_UNSOLVED) {
                        difficultyMap[cellIdx] = level;
                    }

                    lastConfirmedCells = Collections.singleton(cellIdx);
                    changed = true;
                    currentRound++;
                }
            }
        }
    }

    // =========================================================================
    // ログ記録
    // =========================================================================

    private void logDeduction(DeductionResult result) {
        Map<Integer, Integer> newDepths = new HashMap<>();

        for (Map.Entry<Integer, Integer> entry : result.deduced.entrySet()) {
            int cell = entry.getKey();
            Region r = result.cellToSourceRegion.get(cell);
            int depth = calculateDepth(r);
            newDepths.put(cell, depth);
        }

        for (Map.Entry<Integer, Integer> entry : result.deduced.entrySet()) {
            int cell = entry.getKey();
            int val = entry.getValue();
            Region r = result.cellToSourceRegion.get(cell);
            int level = Math.max(LV_1, r.getOriginLevel());
            int depth = newDepths.get(cell);
            String type = (val == FLAGGED) ? "MINE" : "SAFE";

            logger.logStep(currentRound, cell, type, level,
                    r.getId(), r.toLogString(), r.getSourceHintsString(),
                    r.getTriggerCellsString(), r.getParentRegionSnapshot(), depth);
        }

        cellDepthMap.putAll(newDepths);
    }

    private int calculateDepth(Region r) {
        Set<Integer> triggers = r.getTriggerCells();
        if (triggers.isEmpty()) {
            return 1;
        }
        int maxTriggerDepth = 0;
        for (int trigger : triggers) {
            int triggerDepth = cellDepthMap.getOrDefault(trigger, 0);
            maxTriggerDepth = Math.max(maxTriggerDepth, triggerDepth);
        }
        return maxTriggerDepth + 1;
    }

    private int calculateDepthForLv4(int cellIdx) {
        List<Integer> neighbors = getNeighbors(cellIdx);
        int maxNeighborDepth = 0;
        for (int nb : neighbors) {
            if (cellDepthMap.containsKey(nb)) {
                int nbDepth = cellDepthMap.get(nb);
                if (nbDepth > 0) {
                    maxNeighborDepth = Math.max(maxNeighborDepth, nbDepth);
                }
            }
        }
        return maxNeighborDepth + 1;
    }

    private String getTriggerCellsForLv4(int cellIdx) {
        List<Integer> neighbors = getNeighbors(cellIdx);
        List<Integer> triggers = new ArrayList<>();
        for (int nb : neighbors) {
            if (cellDepthMap.containsKey(nb) && cellDepthMap.get(nb) > 0) {
                triggers.add(nb);
            }
        }
        Collections.sort(triggers);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < triggers.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append(triggers.get(i));
        }
        return sb.toString();
    }

    public void exportLogToCSV(String filename) {
        logger.exportToCSV(filename);
    }

    // =========================================================================
    // Region管理
    // =========================================================================

    private Map<Set<Integer>, Region> updateAndGenerateRegions(
            int[] targetBoard,
            Map<Set<Integer>, Region> targetPool,
            boolean generateDerived,
            Set<Integer> confirmedCells) {

        Map<Set<Integer>, Region> nextPool = new HashMap<>();

        for (Region r : targetPool.values()) {
            if (r.getOriginLevel() == LV_1)
                continue;
            Region updated = updateRegionState(targetBoard, r, confirmedCells);
            if (updated != null && !updated.getCells().isEmpty()) {
                nextPool.put(updated.getCells(), updated);
            }
        }

        List<Region> baseRegions = new ArrayList<>();
        for (int i = 0; i < targetBoard.length; i++) {
            if (targetBoard[i] >= 0) {
                Region r = createRegionFromHint(targetBoard, i, confirmedCells);
                if (r != null) {
                    baseRegions.add(r);
                    addToPool(nextPool, r);
                }
            }
        }

        if (generateDerived) {
            for (int i = 0; i < baseRegions.size(); i++) {
                for (int j = i + 1; j < baseRegions.size(); j++) {
                    Region rA = baseRegions.get(i);
                    Region rB = baseRegions.get(j);

                    if (rA.isSubsetOf(rB)) {
                        Region diff = rB.subtract(rA, LV_2);
                        if (!diff.getCells().isEmpty())
                            addToPool(nextPool, diff);
                    } else if (rB.isSubsetOf(rA)) {
                        Region diff = rA.subtract(rB, LV_2);
                        if (!diff.getCells().isEmpty())
                            addToPool(nextPool, diff);
                    } else {
                        Set<Region> intersections = rA.intersect(rB, LV_3);
                        for (Region r : intersections) {
                            addToPool(nextPool, r);
                        }
                    }
                }
            }
        }

        reassignIds(nextPool);
        return nextPool;
    }

    private Region updateRegionState(int[] targetBoard, Region original, Set<Integer> confirmedCells) {
        Set<Integer> currentCells = new HashSet<>();
        Set<Integer> triggerCells = new HashSet<>();
        int currentMines = original.getMines();

        for (int cell : original.getCells()) {
            int val = targetBoard[cell];
            if (val == MINE) {
                currentCells.add(cell);
            } else if (val == FLAGGED) {
                currentMines--;
                if (confirmedCells.contains(cell)) {
                    triggerCells.add(cell);
                }
            } else if (val == SAFE) {
                if (confirmedCells.contains(cell)) {
                    triggerCells.add(cell);
                }
            }
        }

        if (triggerCells.isEmpty() &&
                currentCells.size() == original.getCells().size() &&
                currentMines == original.getMines()) {
            return original;
        }

        String parentSnapshot = original.toLogString();
        int newDepth = original.getGenerationDepth() + 1;

        Region updated = new Region(
                currentCells, currentMines, original.getOriginLevel(),
                triggerCells, parentSnapshot, newDepth);
        updated.addSourceHints(parseSourceHints(original.getSourceHintsString()));

        return updated;
    }

    private Set<Integer> parseSourceHints(String str) {
        Set<Integer> hints = new HashSet<>();
        if (str.equals("Derived") || str.isEmpty())
            return hints;
        String[] parts = str.split(",");
        for (String p : parts) {
            try {
                hints.add(Integer.parseInt(p));
            } catch (Exception e) {
            }
        }
        return hints;
    }

    private void addToPool(Map<Set<Integer>, Region> targetPool, Region newRegion) {
        Set<Integer> key = newRegion.getCells();
        if (targetPool.containsKey(key)) {
            Region existing = targetPool.get(key);
            if (newRegion.getOriginLevel() < existing.getOriginLevel()) {
                targetPool.put(key, newRegion);
            }
        } else {
            targetPool.put(key, newRegion);
        }
    }

    private void reassignIds(Map<Set<Integer>, Region> targetPool) {
        int id = 0;
        List<Region> list = new ArrayList<>(targetPool.values());
        list.sort(Comparator.comparingInt(Region::getOriginLevel)
                .thenComparingInt(Region::hashCode));
        for (Region r : list) {
            r.setId(++id);
        }
    }

    // =========================================================================
    // 推論処理
    // =========================================================================

    private DeductionResult solveFromPool(int[] targetBoard, Map<Set<Integer>, Region> targetPool) {
        DeductionResult result = new DeductionResult();

        List<Region> sortedRegions = new ArrayList<>(targetPool.values());
        sortedRegions.sort(Comparator.comparingInt(Region::getOriginLevel));

        int currentLevel = -1;

        for (Region r : sortedRegions) {
            int regionLevel = r.getOriginLevel();

            if (currentLevel != -1 && regionLevel > currentLevel && !result.isEmpty()) {
                result.level = currentLevel;
                return result;
            }

            currentLevel = regionLevel;

            boolean determined = false;
            int valToSet = -99;

            if (r.getMines() == r.size()) {
                determined = true;
                valToSet = FLAGGED;
            } else if (r.getMines() == 0) {
                determined = true;
                valToSet = SAFE;
            }

            if (determined) {
                for (int cell : r.getCells()) {
                    if (targetBoard[cell] == MINE) {
                        if (!result.deduced.containsKey(cell)) {
                            result.deduced.put(cell, valToSet);
                            result.cellToSourceRegion.put(cell, r);
                        }
                    }
                }
            }
        }

        if (!result.isEmpty()) {
            result.level = currentLevel;
        }

        return result;
    }

    private void applyResult(Map<Integer, Integer> deduced) {
        for (Map.Entry<Integer, Integer> entry : deduced.entrySet()) {
            board[entry.getKey()] = entry.getValue();
        }
    }

    private Region createRegionFromHint(int[] targetBoard, int hintIdx, Set<Integer> confirmedCells) {
        if (targetBoard[hintIdx] < 0)
            return null;

        int hintVal = targetBoard[hintIdx];
        List<Integer> neighbors = getNeighbors(hintIdx);
        Set<Integer> unknownCells = new HashSet<>();
        Set<Integer> triggerCells = new HashSet<>();
        int flaggedCount = 0;

        for (int nb : neighbors) {
            int val = targetBoard[nb];
            if (val == MINE) {
                unknownCells.add(nb);
            } else if (val == FLAGGED) {
                flaggedCount++;
                if (confirmedCells.contains(nb)) {
                    triggerCells.add(nb);
                }
            } else if (val == SAFE) {
                if (confirmedCells.contains(nb)) {
                    triggerCells.add(nb);
                }
            }
        }

        if (unknownCells.isEmpty())
            return null;
        int remainingMines = hintVal - flaggedCount;

        Region r;
        if (!triggerCells.isEmpty()) {
            r = new Region(unknownCells, remainingMines, LV_1, triggerCells, "", 0);
        } else {
            r = new Region(unknownCells, remainingMines, LV_1);
        }
        r.addSourceHint(hintIdx);
        return r;
    }

    private List<Integer> getNeighbors(int idx) {
        List<Integer> list = new ArrayList<>();
        int r = idx / size;
        int c = idx % size;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0)
                    continue;
                int nr = r + dr;
                int nc = c + dc;
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    list.add(nr * size + nc);
                }
            }
        }
        return list;
    }

    public int[] getDifficultyMap() {
        return difficultyMap;
    }

    // =========================================================================
    // Lv4-6 背理法
    // =========================================================================

    private int[] solveLv4() {
        List<Integer> unknownCells = new ArrayList<>();
        for (int i = 0; i < board.length; i++) {
            if (board[i] == MINE) {
                unknownCells.add(i);
            }
        }

        for (int cellIdx : unknownCells) {
            int correctValue = completeBoard[cellIdx];
            boolean isMine = (correctValue == MINE || correctValue == FLAGGED);

            int wrongValue;
            String wrongValueStr;
            if (isMine) {
                wrongValue = SAFE;
                wrongValueStr = "SAFE";
            } else {
                wrongValue = FLAGGED;
                wrongValueStr = "MINE";
            }

            int[] tempBoard = Arrays.copyOf(board, board.length);
            tempBoard[cellIdx] = wrongValue;

            int contradictionLevel = testContradiction(tempBoard);

            if (contradictionLevel > 0) {
                int confirmedValue;
                String type;
                String assumedType;

                if (isMine) {
                    confirmedValue = FLAGGED;
                    type = "MINE";
                    assumedType = "SAFE";
                } else {
                    confirmedValue = SAFE;
                    type = "SAFE";
                    assumedType = "MINE";
                }

                int finalLevel = contradictionLevel + 3;
                int depth = calculateDepthForLv4(cellIdx);

                cellDepthMap.put(cellIdx, depth);

                String triggerCellsStr = getTriggerCellsForLv4(cellIdx);

                logger.logStep(currentRound, cellIdx, type, finalLevel, -1,
                        "Lv" + finalLevel + "-Contradiction", "Assumed:" + assumedType,
                        triggerCellsStr, "", depth);

                return new int[] { cellIdx, confirmedValue, finalLevel };
            }
        }

        return null;
    }

    private int testContradiction(int[] tempBoard) {
        Map<Set<Integer>, Region> tempPool = new HashMap<>();
        int maxLevelUsed = 0;

        while (true) {
            tempPool = updateAndGenerateRegions(tempBoard, tempPool, true, Collections.emptySet());

            int contradictionLevel = checkContradiction(tempBoard, tempPool);
            if (contradictionLevel > 0) {
                return Math.max(maxLevelUsed, contradictionLevel);
            }

            SolveResult result = solveFromPoolForContradiction(tempBoard, tempPool);

            if (result.solvedCount > 0) {
                maxLevelUsed = Math.max(maxLevelUsed, result.maxLevel);
            } else {
                break;
            }
        }

        return -1;
    }

    private int checkContradiction(int[] targetBoard, Map<Set<Integer>, Region> targetPool) {
        for (Region r : targetPool.values()) {
            if (r.getMines() < 0) {
                return r.getOriginLevel();
            }
            if (r.getMines() > r.size()) {
                return r.getOriginLevel();
            }
        }

        for (int i = 0; i < targetBoard.length; i++) {
            if (targetBoard[i] < 0)
                continue;

            int hintValue = targetBoard[i];
            List<Integer> neighbors = getNeighbors(i);

            int unknownCount = 0;
            int flaggedCount = 0;

            for (int nb : neighbors) {
                int val = targetBoard[nb];
                if (val == MINE) {
                    unknownCount++;
                } else if (val == FLAGGED) {
                    flaggedCount++;
                }
            }

            int remainingMines = hintValue - flaggedCount;

            if (remainingMines < 0) {
                return LV_1;
            }
            if (remainingMines > unknownCount) {
                return LV_1;
            }
        }

        return -1;
    }

    private SolveResult solveFromPoolForContradiction(int[] targetBoard, Map<Set<Integer>, Region> targetPool) {
        int solvedCount = 0;
        int maxLevel = 0;
        Map<Integer, Integer> deducedLevel = new HashMap<>();

        List<Region> sortedRegions = new ArrayList<>(targetPool.values());
        sortedRegions.sort(Comparator.comparingInt(Region::getOriginLevel));

        for (Region r : sortedRegions) {
            if (solvedCount > 0) {
                boolean hasLv1Deduction = false;
                for (int level : deducedLevel.values()) {
                    if (level == LV_1) {
                        hasLv1Deduction = true;
                        break;
                    }
                }
                if (r.getOriginLevel() > LV_1 && hasLv1Deduction) {
                    return new SolveResult(solvedCount, maxLevel);
                }
            }

            boolean determined = false;
            int valToSet = -99;

            if (r.getMines() == r.size()) {
                determined = true;
                valToSet = FLAGGED;
            } else if (r.getMines() == 0) {
                determined = true;
                valToSet = SAFE;
            }

            if (determined) {
                int level = Math.max(LV_1, r.getOriginLevel());
                for (int cell : r.getCells()) {
                    if (targetBoard[cell] == MINE) {
                        targetBoard[cell] = valToSet;
                        solvedCount++;
                        maxLevel = Math.max(maxLevel, level);
                        deducedLevel.put(cell, level);
                    }
                }
            }
        }

        return new SolveResult(solvedCount, maxLevel);
    }

    private static class SolveResult {
        final int solvedCount;
        final int maxLevel;

        SolveResult(int solvedCount, int maxLevel) {
            this.solvedCount = solvedCount;
            this.maxLevel = maxLevel;
        }
    }

    public void printCurrentBoard(String label) {
        System.out.println("--- Board State [" + label + "] ---");
        for (int i = 0; i < board.length; i++) {
            int val = board[i];
            if (val == MINE)
                System.out.print(" . ");
            else if (val == FLAGGED)
                System.out.print(" F ");
            else if (val == SAFE)
                System.out.print(" S ");
            else if (val == IGNORE)
                System.out.print(" - ");
            else
                System.out.printf(" %d ", val);
            if ((i + 1) % size == 0)
                System.out.println();
        }
        System.out.println("-----------------------------------");
    }
}