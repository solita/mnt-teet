describe("Task files", function() {

    beforeEach(() => {
        cy.request({method: "POST",
                    url: "/testsetup/task",
                    body: {"project-name": "TASK TESTING"}})
            .then((response) => {
                cy.wrap(response.body["task-url"]).as("taskURL")
            })
    })

/*    it("shows the task export menu item", function() {
        cy.dummyLogin("Danny")
        cy.visit(this.taskURL)
        cy.get("#project-export-menu").click()
        cy.get("#export-task-files").should("not.exist")

        cy.get("[data-cy=task-file-upload]").click()
        cy.uploadFile({inputSelector: "input[id=files-field]",
                       fixturePath: "text_file.jpg",
                       mimeType: "image/jpeg",
                       fileName: "somefile.jpeg"})
        cy.get("button[type=submit]").click()
        cy.get("[data-cy=task-file-list] a", {timeout: 30000}).contains("somefile")

        // export menu item is present now
        cy.get("#project-export-menu").click()
        cy.get("#export-task-files").should("exist")

    })

    it("Can upload files to task", function() {
        cy.dummyLogin("Danny")
        cy.visit(this.taskURL)

        cy.get("[data-cy=task-file-upload]").click()
        cy.uploadFile({inputSelector: "input[id=files-field]",
                       fixturePath: "text_file.jpg",
                       mimeType: "image/jpeg",
                       fileName: "testpicture.jpeg"})

        cy.get("input[value=testpicture]")

        // select reports
        cy.get("option[data-item=':file.document-group/reports']")
            .then(($opt) => {
            cy.get("[data-form-attribute=':task/files'] select").select($opt.attr("value"))
        })

        cy.get("[data-form-attribute=':task/files'] input[type=number]").type(420)

        cy.get("button[type=submit]").click()

        // verify file is in the list

        cy.get("[data-cy=task-file-list] a", {timeout: 30000}).contains("testpicture")
        cy.get("[data-cy=task-file-list] .file-identifying-info").contains("#420")

        // download file and store the filename
        cy.get("[data-cy=task-file-list] a span")
            .contains("cloud_download")
            .then(($dl) => {
                // check download link
                let url = $dl.parent().parent().attr("href")
                cy.request({method: "GET",
                            url: url,
                            followRedirect: true})
                    .then((res) => {
                        // check the response headers
                        let h = res.headers["content-disposition"]
                        let filename = h.substring("attachment; filename=".length)
                        cy.wrap(filename)
                            .should("contain", "MA") // begins
                            .should("contain", "_00_3-420_") // part, doc group and seq#
                            .should("contain", "testpicture.jpeg")
                            .as("filename")

                    })
            })

        cy.get("@filename").then((filename) => {
            // upload again to get new version
            cy.get("[data-cy=task-file-upload]").click()
            cy.uploadFile({inputSelector: "input[id=files-field]",
                           fixturePath: "text_file.jpg",
                           mimeType: "image/jpeg",
                           fileName: filename})
        })

        // verify that there's an info message about replacing
        cy.get("[data-cy=new-version]")

        // submit to upload new version
        cy.get("button[type=submit]").click()


        // wait for dialog to close
        cy.get("button[type=submit]").should("not.exist")

        // verify there is still only one
        cy.get(".file-info").its("length").should("equal", 1)

        cy.get(".file-identifying-info[data-version=1]").should("not.exist")
        cy.get(".file-identifying-info[data-version=2]").should("exist")
    })
*/
    it("Test if task part can be reviewed", function() {
        cy.dummyLogin("Danny")
        cy.visit(this.taskURL)

        // Add file part
        cy.get("[data-cy=task-add-file-part]", {timeout:2000}).click()
        cy.get("input[id=task-part-name]").type("Test-Part-1")
        cy.get("button[type=submit]", {timeout:1000}).click()

        cy.get("[id^=tp-upload-", {timeout:1000}).click()
        cy.uploadFile({inputSelector: "input[id=files-field]",
            fixturePath: "text_file.jpg",
            mimeType: "image/jpeg",
            fileName: "testpicture.jpeg"})

        cy.get("button[type=submit]").click()

        cy.get("[data-file-description=testpicture]", {timeout: 30000}).should("exist")

        // Add Danny as responsible person
        cy.get("[id=edit-task-button").click()
        cy.get("[id=select-user]").type("Danny")
        cy.get("[class=select-user-list", {timeout:3000}).first().click()
        cy.get("button[type=submit]").click()

        cy.get("[id^=submit-button-", {timeout: 5000}).click({force:true})
        cy.get("button[id=confirmation-confirm]").click()

        cy.get("[id^=accept-button-", {timeout: 5000}).click()
        cy.get("button[id=confirmation-confirm]").click()

        cy.get("[id^=reopen-button-", {timeout: 3000}).click()
        cy.get("button[id=confirmation-confirm]").click()

        cy.get("[id^=submit-button-", {timeout: 3000}).click()
        cy.get("button[id=confirmation-confirm]").click()

        cy.get("[id^=reject-button-", {timeout: 3000}).click()
        cy.get("button[id=confirmation-confirm]").click()

    })

})
