package com.example.mazeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MazeView extends View {

    public enum Direction { UP, DOWN, LEFT, RIGHT }

    public enum Difficulty {
        EASY(9), MEDIUM(15), HARD(21);
        public final int size;
        Difficulty(int size) { this.size = size; }
    }

    public interface OnGameUpdateListener {
        void onUpdate(int moves, boolean won);
    }

    private Difficulty difficulty = Difficulty.EASY;
    private int rows = difficulty.size;
    private int cols = difficulty.size;
    private int[][] maze;

    private int playerRow = 1;
    private int playerCol = 1;
    private int exitRow;
    private int exitCol;
    private int movesCount = 0;

    private boolean fogOfWar = false;
    private boolean hintVisible = false;
    private final int visionRadius = 2;
    private final List<int[]> hintPath = new ArrayList<>();

    private OnGameUpdateListener gameUpdateListener;
    private final GestureDetector gestureDetector;

    // Палітра темно-синьої неонової теми
    private final Paint wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wallHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playerHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playerCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint exitHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint exitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fogPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MazeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        wallPaint.setColor(Color.parseColor("#1E3A5F"));
        wallHighlightPaint.setColor(Color.parseColor("#2B5282"));
        pathPaint.setColor(Color.parseColor("#0F172A"));

        playerHaloPaint.setColor(Color.parseColor("#5538BDF8"));
        playerPaint.setColor(Color.parseColor("#38BDF8"));
        playerCorePaint.setColor(Color.parseColor("#E0F2FE"));

        exitHaloPaint.setColor(Color.parseColor("#4410B981"));
        exitPaint.setColor(Color.parseColor("#10B981"));

        hintPaint.setColor(Color.parseColor("#60A5FA"));
        fogPaint.setColor(Color.parseColor("#020617"));

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 40;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD) {
                        movePlayer(diffX > 0 ? Direction.RIGHT : Direction.LEFT);
                        return true;
                    }
                } else {
                    if (Math.abs(diffY) > SWIPE_THRESHOLD) {
                        movePlayer(diffY > 0 ? Direction.DOWN : Direction.UP);
                        return true;
                    }
                }
                return false;
            }
        });

        generateNewMaze();
    }

    // Збереження стану при зміні мови / повороті екрана
    public void saveState(Bundle outState) {
        outState.putInt("maze_diff", difficulty.ordinal());
        outState.putInt("maze_pRow", playerRow);
        outState.putInt("maze_pCol", playerCol);
        outState.putInt("maze_moves", movesCount);
        outState.putBoolean("maze_fog", fogOfWar);
        outState.putBoolean("maze_hint", hintVisible);

        int[] flat = new int[rows * cols];
        for (int r = 0; r < rows; r++) {
            System.arraycopy(maze[r], 0, flat, r * cols, cols);
        }
        outState.putIntArray("maze_grid", flat);
    }

    // Відновлення стану
    public void restoreState(Bundle state) {
        difficulty = Difficulty.values()[state.getInt("maze_diff", 0)];
        rows = difficulty.size;
        cols = difficulty.size;
        exitRow = rows - 2;
        exitCol = cols - 2;
        playerRow = state.getInt("maze_pRow", 1);
        playerCol = state.getInt("maze_pCol", 1);
        movesCount = state.getInt("maze_moves", 0);
        fogOfWar = state.getBoolean("maze_fog", false);
        hintVisible = state.getBoolean("maze_hint", false);

        int[] flat = state.getIntArray("maze_grid");
        if (flat != null && flat.length == rows * cols) {
            maze = new int[rows][cols];
            for (int r = 0; r < rows; r++) {
                System.arraycopy(flat, r * cols, maze[r], 0, cols);
            }
        }
        if (hintVisible) {
            calculateHintPath();
        } else {
            hintPath.clear();
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    public void setOnGameUpdateListener(OnGameUpdateListener listener) {
        this.gameUpdateListener = listener;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.rows = difficulty.size;
        this.cols = difficulty.size;
        AppLogger.getLogger().info("Difficulty changed to " + difficulty.name());
        generateNewMaze();
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public int getMovesCount() {
        return movesCount;
    }

    public void toggleFogOfWar() {
        this.fogOfWar = !this.fogOfWar;
        AppLogger.getLogger().info("Fog of War set to: " + fogOfWar);
        invalidate();
    }

    public boolean isFogOfWar() {
        return fogOfWar;
    }

    public void toggleHint() {
        hintVisible = !hintVisible;
        if (hintVisible) {
            calculateHintPath();
            AppLogger.getLogger().info("Hint enabled: " + hintPath.size() + " steps to exit.");
        } else {
            hintPath.clear();
            AppLogger.getLogger().info("Hint disabled.");
        }
        invalidate();
    }

    public boolean isHintVisible() {
        return hintVisible;
    }

    public void generateNewMaze() {
        maze = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            Arrays.fill(maze[r], 1);
        }
        carve(1, 1);
        playerRow = 1;
        playerCol = 1;
        exitRow = rows - 2;
        exitCol = cols - 2;
        movesCount = 0;
        hintVisible = false;
        hintPath.clear();

        AppLogger.getLogger().info("Generated " + rows + "x" + cols + " maze.");
        if (gameUpdateListener != null) {
            gameUpdateListener.onUpdate(movesCount, false);
        }
        invalidate();
    }

    private void carve(int r, int c) {
        maze[r][c] = 0;
        List<int[]> dirs = Arrays.asList(
                new int[]{-2, 0}, new int[]{2, 0}, new int[]{0, -2}, new int[]{0, 2}
        );
        Collections.shuffle(dirs);

        for (int[] d : dirs) {
            int nr = r + d[0];
            int nc = c + d[1];
            if (nr > 0 && nr < rows - 1 && nc > 0 && nc < cols - 1 && maze[nr][nc] == 1) {
                maze[r + d[0] / 2][c + d[1] / 2] = 0;
                carve(nr, nc);
            }
        }
    }

    public void movePlayer(Direction direction) {
        int dr = 0, dc = 0;
        switch (direction) {
            case UP:    dr = -1; break;
            case DOWN:  dr = 1;  break;
            case LEFT:  dc = -1; break;
            case RIGHT: dc = 1;  break;
        }

        int nextR = playerRow + dr;
        int nextC = playerCol + dc;

        if (nextR >= 0 && nextR < rows && nextC >= 0 && nextC < cols && maze[nextR][nextC] == 0) {
            playerRow = nextR;
            playerCol = nextC;
            movesCount++;

            if (hintVisible) {
                calculateHintPath();
            }

            AppLogger.getLogger().fine("Moved " + direction + " to (" + playerRow + ", " + playerCol + ")");

            boolean won = (playerRow == exitRow && playerCol == exitCol);
            if (won) {
                hintVisible = false;
                hintPath.clear();
                AppLogger.getLogger().info("Maze solved in " + movesCount + " moves!");
            }
            if (gameUpdateListener != null) {
                gameUpdateListener.onUpdate(movesCount, won);
            }
            invalidate();
        } else {
            AppLogger.getLogger().warning("Wall collision at (" + nextR + ", " + nextC + ")");
        }
    }

    private void calculateHintPath() {
        hintPath.clear();
        boolean[][] visited = new boolean[rows][cols];
        int[][][] parent = new int[rows][cols][2];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                parent[r][c][0] = -1;
                parent[r][c][1] = -1;
            }
        }

        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{playerRow, playerCol});
        visited[playerRow][playerCol] = true;

        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        boolean found = false;

        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            if (curr[0] == exitRow && curr[1] == exitCol) {
                found = true;
                break;
            }
            for (int[] d : dirs) {
                int nr = curr[0] + d[0];
                int nc = curr[1] + d[1];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && maze[nr][nc] == 0 && !visited[nr][nc]) {
                    visited[nr][nc] = true;
                    parent[nr][nc][0] = curr[0];
                    parent[nr][nc][1] = curr[1];
                    queue.add(new int[]{nr, nc});
                }
            }
        }

        if (found) {
            int r = exitRow, c = exitCol;
            while (r != -1 && c != -1) {
                hintPath.add(new int[]{r, c});
                int pr = parent[r][c][0];
                int pc = parent[r][c][1];
                r = pr;
                c = pc;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cellSize = Math.min(getWidth() / (float) cols, getHeight() / (float) rows);
        float offsetX = (getWidth() - cellSize * cols) / 2f;
        float offsetY = (getHeight() - cellSize * rows) / 2f;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float left = offsetX + c * cellSize;
                float top = offsetY + r * cellSize;

                if (fogOfWar) {
                    int dist = Math.abs(r - playerRow) + Math.abs(c - playerCol);
                    if (dist > visionRadius && !(r == exitRow && c == exitCol)) {
                        canvas.drawRect(left, top, left + cellSize + 1, top + cellSize + 1, fogPaint);
                        continue;
                    }
                }

                if (maze[r][c] == 1) {
                    canvas.drawRect(left, top, left + cellSize + 1, top + cellSize + 1, wallPaint);
                    // Легкий об'ємний блік на стінах
                    canvas.drawRect(left + 1, top + 1, left + cellSize - 1, top + cellSize * 0.18f, wallHighlightPaint);
                } else {
                    canvas.drawRect(left, top, left + cellSize + 1, top + cellSize + 1, pathPaint);
                }
            }
        }

        // Неонові крапки підказки
        for (int[] cell : hintPath) {
            if ((cell[0] == playerRow && cell[1] == playerCol) || (cell[0] == exitRow && cell[1] == exitCol)) {
                continue;
            }
            canvas.drawCircle(
                    offsetX + (cell[1] + 0.5f) * cellSize,
                    offsetY + (cell[0] + 0.5f) * cellSize,
                    cellSize * 0.16f,
                    hintPaint
            );
        }

        // Вихід (портал зі світінням)
        float exitCx = offsetX + (exitCol + 0.5f) * cellSize;
        float exitCy = offsetY + (exitRow + 0.5f) * cellSize;
        canvas.drawCircle(exitCx, exitCy, cellSize * 0.44f, exitHaloPaint);
        RectF exitRect = new RectF(
                offsetX + exitCol * cellSize + cellSize * 0.18f,
                offsetY + exitRow * cellSize + cellSize * 0.18f,
                offsetX + (exitCol + 1) * cellSize - cellSize * 0.18f,
                offsetY + (exitRow + 1) * cellSize - cellSize * 0.18f
        );
        canvas.drawRoundRect(exitRect, cellSize * 0.22f, cellSize * 0.22f, exitPaint);

        // Гравець (неонова сфера)
        float playerCx = offsetX + (playerCol + 0.5f) * cellSize;
        float playerCy = offsetY + (playerRow + 0.5f) * cellSize;
        canvas.drawCircle(playerCx, playerCy, cellSize * 0.42f, playerHaloPaint);
        canvas.drawCircle(playerCx, playerCy, cellSize * 0.30f, playerPaint);
        canvas.drawCircle(playerCx - cellSize * 0.08f, playerCy - cellSize * 0.08f, cellSize * 0.10f, playerCorePaint);
    }
}