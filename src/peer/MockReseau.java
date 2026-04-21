package peer;
public class MockReseau implements IReseau {

    @Override
    public void envoyerMessage(String msg) {
        System.out.println("[MOCK RESEAU] " + msg);
    }
}