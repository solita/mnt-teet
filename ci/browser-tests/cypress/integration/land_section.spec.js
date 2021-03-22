context('Land section', () => {
  beforeEach(() => {
      cy.visit("")
      // let's make sure we're using the language we're asserting in?
      cy.get("#language-select").select("ET")
      cy.get("#password-textfield").type(Cypress.env("SITE_PASSWORD"));
      cy.get("button").contains("Login as Benjamin Boss").click();
      
      cy.contains('Minu projektid');      
    })

  it("select project", () => {
    // the menu is "hidden" by default, so let's open it
    cy.get("span").contains("menu").click({force: true});
    cy.get("span").contains("Projektide loetelu").click();
    cy.contains("Kõik projektid").click();
    cy.get('thead > tr > th:nth-child(1) > label > div > input').type("aruvalla");
    cy.get("p").contains("Aruvalla").click();
    cy.get("main span.MuiIconButton-label > span.material-icons").click();
    cy.get("li.MuiButtonBase-root:nth-child(6) > div:nth-child(1)").click();
    cy.contains("Vaatan omanike andmeid", {timeout: 20000}).click();
    cy.contains("Omanikud ja omandiosad");
  });

})
