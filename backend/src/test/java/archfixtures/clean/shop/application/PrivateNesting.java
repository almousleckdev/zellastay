package archfixtures.clean.shop.application;

public class PrivateNesting {

  private record Outcome(int value) {}

  public int run() {
    return new Outcome(1).value();
  }
}
