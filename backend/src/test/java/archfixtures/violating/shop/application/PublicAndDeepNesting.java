package archfixtures.violating.shop.application;

public class PublicAndDeepNesting {

  public record Result(int value) {}

  private static final class Outer {
    private static final class Inner {}
  }
}
