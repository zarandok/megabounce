package cz.cvut.fel.pjv;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static javafx.scene.paint.Color.BLACK;
import static javafx.scene.paint.Color.BLUE;
import static javafx.scene.paint.Color.BROWN;
import static javafx.scene.paint.Color.GREEN;
import static javafx.scene.paint.Color.PINK;
import static javafx.scene.paint.Color.RED;
import static javafx.scene.paint.Color.YELLOW;

import java.util.ListIterator;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

// copied from: https://gist.github.com/james-d/8327842
// and modified to use canvas drawing instead of shapes

public class MainCanvas extends Application {

  private List<Ball> balls = new ArrayList();
  private static final int NUM_BALLS = 400;
  private static final double MIN_RADIUS = 5;
  private static final double MAX_RADIUS = 15;
  private static final double MIN_SPEED = 50;
  private static final double MAX_SPEED = 250;
  private static final Color[] COLORS = new Color[]{RED, YELLOW, GREEN,
    BROWN, BLUE, PINK, BLACK};

  private final FrameStats frameStats = new FrameStats();

  @Override
  public void start(Stage primaryStage) {
    final Canvas ballContainer = new Canvas();
    
    constrainBallsOnResize(ballContainer);

    ballContainer.addEventHandler(MouseEvent.MOUSE_CLICKED,
      new EventHandler<MouseEvent>() {
      @Override
      public void handle(MouseEvent event) {
        if (event.getClickCount() == 2) {
          balls.clear();
          createBalls(NUM_BALLS, MIN_RADIUS, MAX_RADIUS, MIN_SPEED, MAX_SPEED, ballContainer.getWidth() / 2, ballContainer.getHeight() / 2);
        }
      }
    });

    createBalls(NUM_BALLS, MIN_RADIUS, MAX_RADIUS, MIN_SPEED, MAX_SPEED, 400, 300);

    final BorderPane root = new BorderPane();

    final Pane canvasContainer  = new Pane(ballContainer);
    ballContainer.widthProperty().bind(canvasContainer.widthProperty());
    ballContainer.heightProperty().bind(canvasContainer.heightProperty());

    final Label stats = new Label();
    stats.textProperty().bind(frameStats.textProperty());

    root.setCenter(canvasContainer);
    root.setBottom(stats);

    final Scene scene = new Scene(root, 800, 600);
    primaryStage.setScene(scene);
    primaryStage.show();

    startAnimation(ballContainer);
  }

  private void startAnimation(final Canvas ballContainer) {
    final LongProperty lastUpdateTime = new SimpleLongProperty(0);
    final AnimationTimer timer = new AnimationTimer() {
      @Override
      public void handle(long timestamp) {
        if (lastUpdateTime.get() > 0) {
          long elapsedTime = timestamp - lastUpdateTime.get();
          checkCollisions(ballContainer.getWidth(), ballContainer.getHeight());
          updateWorld(elapsedTime, ballContainer);
          frameStats.addFrame(elapsedTime);
        }
        lastUpdateTime.set(timestamp);
      }
    };
    timer.start();
  }

  private void updateWorld(long elapsedTime, final Canvas ballContainer) {
    final GraphicsContext gc = ballContainer.getGraphicsContext2D();
    double elapsedSeconds = elapsedTime / 1_000_000_000.0;
    gc.setFill(Color.LIGHTGRAY);
    gc.fillRect(0, 0, ballContainer.getWidth(), ballContainer.getHeight());
    for (Ball b : balls) {
      b.setCenterX(b.getCenterX() + elapsedSeconds * b.getXVelocity());
      b.setCenterY(b.getCenterY() + elapsedSeconds * b.getYVelocity());
      gc.setFill(b.getColor());
      double r = b.getRadius();
      gc.fillOval(b.getCenterX() - r, b.getCenterY() - r, 2 * r, 2 * r);
    }
  }

  private void checkCollisions(double maxX, double maxY) {
    for (ListIterator<Ball> slowIt = balls.listIterator(); slowIt.hasNext();) {
      Ball b1 = slowIt.next();
      // check wall collisions:
      double xVel = b1.getXVelocity();
      double yVel = b1.getYVelocity();
      if ((b1.getCenterX() - b1.getRadius() <= 0 && xVel < 0)
        || (b1.getCenterX() + b1.getRadius() >= maxX && xVel > 0)) {
        b1.setXVelocity(-xVel);
      }
      if ((b1.getCenterY() - b1.getRadius() <= 0 && yVel < 0)
        || (b1.getCenterY() + b1.getRadius() >= maxY && yVel > 0)) {
        b1.setYVelocity(-yVel);
      }
      for (ListIterator<Ball> fastIt = balls.listIterator(slowIt.nextIndex()); fastIt.hasNext();) {
        Ball b2 = fastIt.next();
        // performance hack: both colliding(...) and bounce(...) need deltaX and deltaY, so compute them once here:
        final double deltaX = b2.getCenterX() - b1.getCenterX();
        final double deltaY = b2.getCenterY() - b1.getCenterY();
        if (colliding(b1, b2, deltaX, deltaY)) {
          bounce(b1, b2, deltaX, deltaY);
        }
      }
    }
  }

  public boolean colliding(final Ball b1, final Ball b2, final double deltaX, final double deltaY) {
    // square of distance between balls is s^2 = (x2-x1)^2 + (y2-y1)^2
    // balls are "overlapping" if s^2 < (r1 + r2)^2
    // We also check that distance is decreasing, i.e.
    // d/dt(s^2) < 0:
    // 2(x2-x1)(x2'-x1') + 2(y2-y1)(y2'-y1') < 0

    final double radiusSum = b1.getRadius() + b2.getRadius();
    if (deltaX * deltaX + deltaY * deltaY <= radiusSum * radiusSum) {
      if (deltaX * (b2.getXVelocity() - b1.getXVelocity())
        + deltaY * (b2.getYVelocity() - b1.getYVelocity()) < 0) {
        return true;
      }
    }
    return false;
  }

  private void bounce(final Ball b1, final Ball b2, final double deltaX, final double deltaY) {
    final double distance = sqrt(deltaX * deltaX + deltaY * deltaY);
    final double unitContactX = deltaX / distance;
    final double unitContactY = deltaY / distance;

    final double xVelocity1 = b1.getXVelocity();
    final double yVelocity1 = b1.getYVelocity();
    final double xVelocity2 = b2.getXVelocity();
    final double yVelocity2 = b2.getYVelocity();

    final double u1 = xVelocity1 * unitContactX + yVelocity1 * unitContactY; // velocity of ball 1 parallel to contact vector
    final double u2 = xVelocity2 * unitContactX + yVelocity2 * unitContactY; // same for ball 2

    final double massSum = b1.getMass() + b2.getMass();
    final double massDiff = b1.getMass() - b2.getMass();

    final double v1 = (2 * b2.getMass() * u2 + u1 * massDiff) / massSum; // These equations are derived for one-dimensional collision by
    final double v2 = (2 * b1.getMass() * u1 - u2 * massDiff) / massSum; // solving equations for conservation of momentum and conservation of energy

    final double u1PerpX = xVelocity1 - u1 * unitContactX; // Components of ball 1 velocity in direction perpendicular
    final double u1PerpY = yVelocity1 - u1 * unitContactY; // to contact vector. This doesn't change with collision
    final double u2PerpX = xVelocity2 - u2 * unitContactX; // Same for ball 2....
    final double u2PerpY = yVelocity2 - u2 * unitContactY;

    b1.setXVelocity(v1 * unitContactX + u1PerpX);
    b1.setYVelocity(v1 * unitContactY + u1PerpY);
    b2.setXVelocity(v2 * unitContactX + u2PerpX);
    b2.setYVelocity(v2 * unitContactY + u2PerpY);

  }

  private void createBalls(int numBalls, double minRadius, double maxRadius, double minSpeed, double maxSpeed, double initialX, double initialY) {
    final Random rng = new Random();
    for (int i = 0; i < numBalls; i++) {
      double radius = minRadius + (maxRadius - minRadius) * rng.nextDouble();
      double mass = Math.pow((radius / 40), 3);

      final double speed = minSpeed + (maxSpeed - minSpeed) * rng.nextDouble();
      final double angle = 2 * PI * rng.nextDouble();
      Ball ball = new Ball(
        initialX, initialY,
        radius, speed * cos(angle), speed * sin(angle),
        mass, COLORS[i % COLORS.length]
      );
      balls.add(ball);
    }
  }

  private void constrainBallsOnResize(final Canvas ballContainer) {
    ballContainer.widthProperty().addListener(new ChangeListener<Number>() {
      @Override
      public void changed(ObservableValue<? extends Number> observable,
        Number oldValue, Number newValue) {
        if (newValue.doubleValue() < oldValue.doubleValue()) {
          for (Ball b : balls) {
            double max = newValue.doubleValue() - b.getRadius();
            if (b.getCenterX() > max) {
              b.setCenterX(max);
            }
          }
        }
      }
    });

    ballContainer.heightProperty().addListener(new ChangeListener<Number>() {
      @Override
      public void changed(ObservableValue<? extends Number> observable,
        Number oldValue, Number newValue) {
        if (newValue.doubleValue() < oldValue.doubleValue()) {
          for (Ball b : balls) {
            double max = newValue.doubleValue() - b.getRadius();
            if (b.getCenterY() > max) {
              b.setCenterY(max);
            }
          }
        }
      }
    });
  }

  private static class Ball {

    private double centerX; // pixels
    private double centerY;
    private final double radius;
    private double xVelocity; // pixels per second
    private double yVelocity;
    private final double mass; // arbitrary units
    private final Color color;

    public Ball(
      double centerX, double centerY, double radius,
      double xVelocity, double yVelocity,
      double mass, Color color
    ) {
      this.centerX = centerX;
      this.centerY = centerY;
      this.radius = radius;
      this.xVelocity = xVelocity;
      this.yVelocity = yVelocity;
      this.mass = mass;
      this.color = color;
    }

    public double getMass() {
      return mass;
    }

    public double getRadius() {
      return radius;
    }

    public final double getXVelocity() {
      return xVelocity;
    }

    public final void setXVelocity(double xVelocity) {
      this.xVelocity = xVelocity;
    }

    public final double getYVelocity() {
      return yVelocity;
    }

    public final void setYVelocity(double yVelocity) {
      this.yVelocity = yVelocity;
    }

    public final double getCenterX() {
      return centerX;
    }

    public final void setCenterX(double centerX) {
      this.centerX = centerX;
    }

    public final double getCenterY() {
      return centerY;
    }

    public final void setCenterY(double centerY) {
      this.centerY = centerY;
    }

    public Color getColor() {
      return color;
    }
  }

  private static class FrameStats {

    private long frameCount;
    private double meanFrameInterval; // millis
    private final ReadOnlyStringWrapper text = new ReadOnlyStringWrapper(this, "text", "Frame count: 0 Average frame interval: N/A");

    public long getFrameCount() {
      return frameCount;
    }

    public double getMeanFrameInterval() {
      return meanFrameInterval;
    }

    public void addFrame(long frameDurationNanos) {
      meanFrameInterval = (meanFrameInterval * frameCount + frameDurationNanos / 1_000_000.0) / (frameCount + 1);
      frameCount++;
      text.set(toString());
    }

    public String getText() {
      return text.get();
    }

    public ReadOnlyStringProperty textProperty() {
      return text.getReadOnlyProperty();
    }

    @Override
    public String toString() {
      return String.format("Frame count: %,d Average frame interval: %.3f milliseconds", getFrameCount(), getMeanFrameInterval());
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}
