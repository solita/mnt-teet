context('Land section', () => {
  beforeEach(() => {
      cy.visit("./")
      cy.get("#password-textfield").type(Cypress.env("SITE_PASSWORD"));
      cy.get("button").contains("Login as Benjamin Boss").click();
      
      cy.contains('Minu projektid');      
    })

  it("select project", () => {
    cy.get("span").contains("Projektide loetelu").click();
    cy.contains("Kõik projektid").click();
    cy.get('thead > tr > th:nth-child(1) > label > div > input').type("aruvalla");
    cy.get("p").contains("Aruvalla").click();
    cy.get("main span.MuiIconButton-label > span.material-icons").click();
    cy.get("body > div:nth-child(3) > div > ul > li:nth-child(6)").click();
    cy.get("a", {timeout: 20000}).contains("Vaatan omanike andmeid").click();
    cy.get("h2").contains("Omanikud ja omandiosad");
    // empty dialog as of this writing, can continue test when dialog impl is there
  });

})
