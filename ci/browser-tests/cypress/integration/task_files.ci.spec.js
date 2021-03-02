describe("Task files", function() {

    before(() => {
        cy.request({method: "POST",
                    url: "/testsetup/task",
                    body: {"project-name": "TASK TESTING"}})
            .then((response) => {
                cy.wrap(response.body["task-url"]).as("taskURL")
            })
    })

    it("shows the task export menu item", function() {
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

})
